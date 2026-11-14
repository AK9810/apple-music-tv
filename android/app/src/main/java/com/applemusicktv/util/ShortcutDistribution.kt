package com.applemusicktv.util

/**
 * Where the signed iOS Shortcut is published, and how the :8080 page links to it.
 *
 * iOS refuses `http://` targets for the `shortcuts://import-shortcut` scheme and
 * accepts the signed file only over https, so the primary action must use an
 * https copy of the same signed artifact. The TV-local download stays as the
 * offline fallback — a different URL for the identical bytes.
 *
 * The nested URL is percent-encoded exactly once. Two failures are silent on a
 * desktop but break the import on the phone: double encoding makes the
 * Shortcuts app fetch a URL that still contains `%3A`, and form-style encoding
 * turns spaces into `+`, naming the imported shortcut `Send+Token+to+TV`.
 * Both are pinned in `ShortcutDistributionTest`.
 *
 * Dots are encoded as `%2E` too, so the whole inner URL visibly went through the
 * encoder; `%2E` and `.` are equivalent after the Shortcuts app decodes it.
 *
 * Deliberately free of Android imports: plain Kotlin, testable on the JVM.
 */
object ShortcutDistribution {

    /** The https copy of `shortcut/Send Token to TV.shortcut` on the v1.7 release. */
    const val HTTPS_URL =
        "https://github.com/AK9810/apple-music-tv/releases/download/v1.7/Send-Token-to-TV.shortcut"

    /** The imported shortcut's name. The release asset is hyphenated instead. */
    const val NAME = "Send Token to TV"

    /** The import link as a raw URL; the `&` between parameters is not HTML-escaped. */
    fun importDeepLink(): String =
        "shortcuts://import-shortcut?url=${percentEncode(HTTPS_URL)}&name=${percentEncode(NAME)}"

    /** [importDeepLink] escaped for a raw-text HTML attribute: `&` becomes `&amp;`. */
    fun importDeepLinkHtml(): String = importDeepLink().replace("&", "&amp;")

    /**
     * Percent-encodes [value] over its UTF-8 bytes, leaving only alphanumerics,
     * `-`, `_` and `~` unescaped. Spaces become `%20`, never `+`.
     */
    private fun percentEncode(value: String): String = buildString {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val unreserved = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code ||
                c in '0'.code..'9'.code || c == '-'.code || c == '_'.code || c == '~'.code
            if (unreserved) {
                append(c.toChar())
            } else {
                append('%')
                append(HEX[c ushr 4])
                append(HEX[c and 0x0F])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
