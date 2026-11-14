package com.applemusicktv.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URLEncoder

/**
 * Pins the form-body/query-string parsing used by the port-8080 server. The MUT arrives
 * either from the web form or the companion hand-off, and a value that comes back subtly
 * changed is the worst failure mode here: the TV reports "signed in" but shows an empty
 * library, which is very hard to diagnose from the couch.
 */
class FormBodyTest {

    @Test
    fun `reads a plain value`() {
        assertEquals("abc123", FormBody.field("mut=abc123", "mut"))
    }

    @Test
    fun `percent encoded plus decodes to a literal plus`() {
        assertEquals("a+b", FormBody.field("mut=a%2Bb", "mut"))
    }

    @Test
    fun `unencoded plus stays a plus and does not become a space`() {
        assertEquals("a+b", FormBody.field("mut=a+b", "mut"))
    }

    @Test
    fun `missing field returns empty string`() {
        assertEquals("", FormBody.field("offset=120", "mut"))
        assertEquals("", FormBody.field("", "mut"))
    }

    @Test
    fun `extra fields before and after do not disturb the wanted field`() {
        assertEquals("abc", FormBody.field("offset=120&mut=abc&vol=1", "mut"))
        assertEquals("120", FormBody.field("offset=120&mut=abc&vol=1", "offset"))
    }

    @Test
    fun `percent encoded token characters decode`() {
        // Real tokens are base64-ish, so "=" and "/" can arrive percent-encoded.
        assertEquals("a=b/c", FormBody.field("mut=a%3Db%2Fc", "mut"))
    }

    @Test
    fun `mut shaped value round trips through the companion encoding`() {
        // What the companion actually sends: URLEncoder.encode, then FormBody.field.
        val mut = "Aq+/abc==.eyJzdWIiOiIxIn0-_"
        val body = "mut=${URLEncoder.encode(mut, "UTF-8")}"
        assertEquals(mut, FormBody.field(body, "mut"))
    }

    @Test
    fun `malformed percent escape is treated as a missing field`() {
        assertEquals("", FormBody.field("mut=%ZZnot-a-token", "mut"))
    }
}
