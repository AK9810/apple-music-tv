package com.applemusicktv.media.widevine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Guards the streaming decryption path ([CencDecryptor.decryptWholeTo]).
 *
 * The shape of the output matters as much as the decrypted bytes: ExoPlayer seeks using the
 * synthesized `sidx`, whose reference sizes count ONLY the moof+mdat fragments that follow it.
 * A stray box written between fragments (or a fragment written before the index) shifts every
 * later offset and seeking silently lands in the wrong place. The old in-memory implementation
 * got this right by construction; the streaming rewrite has to preserve it explicitly.
 */
class CencDecryptorTest {

    private val keyHex = "000102030405060708090a0b0c0d0e0f"
    private val key = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(8)   // 8-byte IV, zero-filled

    /** Two 16-byte samples in one mdat, each encrypted with its own per-sample IV (as CENC does). */
    private fun plainSamples() = ByteArray(32) { (it * 7 + 3).toByte() }

    private fun encrypt(bytes: ByteArray): ByteArray {
        val iv16 = ByteArray(16)
        System.arraycopy(iv, 0, iv16, 0, iv.size)
        val out = ByteArrayOutputStream()
        // Per-sample CTR: each 16-byte sample restarts the keystream from its own IV. Encrypting
        // both samples as one stream would pass a wrong fixture and fail a correct decryptor.
        for (offset in bytes.indices step 16) {
            val cipher = Cipher.getInstance("AES/CTR/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv16))
            out.write(cipher.doFinal(bytes, offset, 16))
        }
        return out.toByteArray()
    }

    private fun u32(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun box(type: String, payload: ByteArray): ByteArray {
        val size = 8 + payload.size
        val out = ByteArrayOutputStream()
        out.write(u32(size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        return out.toByteArray()
    }

    private fun fullBox(type: String, version: Int, flags: Int, payload: ByteArray): ByteArray {
        val p = ByteArrayOutputStream()
        p.write(u32((version shl 24) or flags))
        p.write(payload)
        return box(type, p.toByteArray())
    }

    /**
     * A minimal but structurally real encrypted fMP4:
     * ftyp, moov(trak/mdia/mdhd + tenc), moof(traf/tfhd/trun/senc), mdat.
     */
    private fun encryptedFmp4(): ByteArray {
        val ftyp = box("ftyp", "isom".toByteArray() + u32(0) + "isomiso2".toByteArray())

        val mdhd = fullBox(
            "mdhd", 0, 0,
            u32(0) + u32(0) + u32(44100) + u32(2048),   // creation, modification, timescale, duration
        )
        // tenc v0: reserved(1) crypt_skip(1) isProtected(1) ivSize(1) kid(16)
        val tenc = fullBox("tenc", 0, 0, byteArrayOf(0, 0, 1, 8) + ByteArray(16))
        val trak = box("trak", box("mdia", mdhd) + box("sinf", tenc))
        val moov = box("moov", trak)

        val tfhd = fullBox("tfhd", 0, 0x8, u32(1) + u32(1024))            // track_ID + default duration
        // trun: flags 0x1 (data_offset) | 0x100 (duration) | 0x200 (size), 2 samples
        val trunPayload = ByteArrayOutputStream().apply {
            write(u32(2))                    // sample_count
            write(u32(0))                    // data_offset (patched by the decryptor)
            write(u32(1024)); write(u32(16))
            write(u32(1024)); write(u32(16))
        }.toByteArray()
        val trun = fullBox("trun", 0, 0x301, trunPayload)
        // senc: 2 samples, each a bare 8-byte IV (no subsamples)
        val senc = fullBox("senc", 0, 0, u32(2) + iv + iv)
        val moof = box("moof", box("traf", tfhd + trun + senc))

        val mdat = box("mdat", encrypt(plainSamples()))
        return ftyp + moov + moof + mdat
    }

    private fun topLevelTypes(bytes: ByteArray): List<String> {
        val types = mutableListOf<String>()
        var i = 0
        while (i + 8 <= bytes.size) {
            val size = ((bytes[i].toInt() and 0xff) shl 24) or
                ((bytes[i + 1].toInt() and 0xff) shl 16) or
                ((bytes[i + 2].toInt() and 0xff) shl 8) or
                (bytes[i + 3].toInt() and 0xff)
            if (size < 8 || i + size > bytes.size) break
            types.add(String(bytes, i + 4, 4, Charsets.US_ASCII))
            i += size
        }
        return types
    }

    private fun payloadOf(bytes: ByteArray, type: String): ByteArray {
        var i = 0
        while (i + 8 <= bytes.size) {
            val size = ((bytes[i].toInt() and 0xff) shl 24) or
                ((bytes[i + 1].toInt() and 0xff) shl 16) or
                ((bytes[i + 2].toInt() and 0xff) shl 8) or
                (bytes[i + 3].toInt() and 0xff)
            if (size < 8 || i + size > bytes.size) break
            if (String(bytes, i + 4, 4, Charsets.US_ASCII) == type) {
                return bytes.copyOfRange(i + 8, i + size)
            }
            i += size
        }
        error("box $type not found")
    }

    @Test
    fun `streaming decrypt restores the plain samples`() {
        val out = ByteArrayOutputStream()
        CencDecryptor(keyHex).decryptWholeTo(encryptedFmp4(), out)
        val result = out.toByteArray()

        assertArrayEquals(plainSamples(), payloadOf(result, "mdat"))
    }

    @Test
    fun `streaming decrypt emits the index before the fragments`() {
        val out = ByteArrayOutputStream()
        CencDecryptor(keyHex).decryptWholeTo(encryptedFmp4(), out)

        // The sidx must sit directly after the head and before any fragment — otherwise its
        // reference sizes (which count only moof+mdat) no longer line up with the file.
        val types = topLevelTypes(out.toByteArray())
        assertEquals(listOf("ftyp", "moov", "sidx", "moof", "mdat"), types)
    }

    @Test
    fun `sidx reference size matches the emitted fragment`() {
        val out = ByteArrayOutputStream()
        CencDecryptor(keyHex).decryptWholeTo(encryptedFmp4(), out)
        val result = out.toByteArray()

        val sidx = payloadOf(result, "sidx")
        // sidx v0: version+flags(4) ref_ID(4) timescale(4) ept(4) first_offset(4)
        //          reserved(2) ref_count(2), then per reference: size(4) duration(4) sap(4)
        val refCount = ((sidx[22].toInt() and 0xff) shl 8) or (sidx[23].toInt() and 0xff)
        assertEquals(1, refCount)
        val refSize = ((sidx[24].toInt() and 0xff) shl 24) or
            ((sidx[25].toInt() and 0xff) shl 16) or
            ((sidx[26].toInt() and 0xff) shl 8) or
            (sidx[27].toInt() and 0xff)

        val moofSize = payloadOf(result, "moof").size + 8
        val mdatSize = payloadOf(result, "mdat").size + 8
        assertEquals(moofSize + mdatSize, refSize)
    }

    @Test
    fun `encryption boxes are stripped from the rewritten moof`() {
        val out = ByteArrayOutputStream()
        CencDecryptor(keyHex).decryptWholeTo(encryptedFmp4(), out)
        val moof = payloadOf(out.toByteArray(), "moof")
        val asText = String(moof, Charsets.ISO_8859_1)

        assertTrue("senc must be dropped so ExoPlayer treats samples as clear", !asText.contains("senc"))
        assertTrue("trun must survive", asText.contains("trun"))
    }
}
