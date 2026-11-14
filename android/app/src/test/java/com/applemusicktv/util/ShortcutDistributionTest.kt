package com.applemusicktv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one-tap import link that the iOS side of the :8080 page shows.
 *
 * iOS refuses `http://` import targets, so the primary action must point at the
 * https copy of the signed artifact. The copy must also answer **directly**: a
 * `releases/download/...` URL 302s to a signed storage URL, and the import
 * handler surfaces that redirect on the phone as "Import Failed" (seen on a real
 * iPhone). Hence the raw pinned-commit URL asserted here.
 *
 * Two other ways to get this wrong are invisible on a desktop: double-encoding
 * the nested URL (the Shortcuts app then cannot fetch the file) and encoding
 * spaces as `+` in the `name` parameter (the imported shortcut is named
 * `Send+Token+to+TV`). Both, plus the exact href, are asserted literally here.
 *
 * The link uses the RFC 3986 unreserved set unchanged — `A-Z a-z 0-9 - . _ ~`
 * stay raw — so the inner URL appears as the ordinary `https%3A%2F%2Fraw...` form
 * rather than with `%2E` for its dots.
 */
class ShortcutDistributionTest {

    @Test
    fun `the published url is the pinned raw copy`() {
        assertEquals(
            "https://raw.githubusercontent.com/AK9810/apple-music-tv/" +
                "68e33888f812b206d8dbf949c959b668eece216a/shortcut/" +
                "Send%20Token%20to%20TV.shortcut",
            ShortcutDistribution.HTTPS_URL,
        )
    }

    @Test
    fun `the published url never redirects`() {
        // A release download answers 302; the import handler cannot follow it. Keep the
        // direct-link trap out of the code no matter how the constant is edited later.
        assertFalse(
            "release downloads redirect and break the import: ${ShortcutDistribution.HTTPS_URL}",
            ShortcutDistribution.HTTPS_URL.contains("releases/download"),
        )
        assertTrue(ShortcutDistribution.HTTPS_URL.startsWith("https://"))
        assertTrue(ShortcutDistribution.HTTPS_URL.endsWith(".shortcut"))
    }

    @Test
    fun `deep link percent encodes the https url exactly once`() {
        assertEquals(
            "shortcuts://import-shortcut?" +
                "url=https%3A%2F%2Fraw.githubusercontent.com%2FAK9810%2Fapple-music-tv%2F" +
                "68e33888f812b206d8dbf949c959b668eece216a%2Fshortcut%2F" +
                "Send%2520Token%2520to%2520TV.shortcut" +
                "&name=Send%20Token%20to%20TV",
            ShortcutDistribution.importDeepLink(),
        )
    }

    @Test
    fun `the nested url decodes back to the published url`() {
        // One decode of the `url` parameter must yield exactly the raw URL the phone
        // fetches. The path's own `%20` correctly survives as `%2520` in this form.
        val encoded = ShortcutDistribution.importDeepLink()
            .substringAfter("url=")
            .substringBefore("&name=")
        assertEquals(
            ShortcutDistribution.HTTPS_URL,
            java.net.URLDecoder.decode(encoded, "UTF-8"),
        )
    }

    @Test
    fun `the nested url is not double encoded and keeps unreserved dots raw`() {
        val link = ShortcutDistribution.importDeepLink()
        assertFalse("scheme got encoded twice: $link", link.contains("%253A"))
        assertFalse("a bare https url survived: $link", link.contains("https://"))
        assertTrue(
            "the inner url should be in the ordinary encoded form: $link",
            link.contains("url=https%3A%2F%2Fraw.githubusercontent.com%2FAK9810%2F"),
        )
        assertFalse("unreserved dots must not be percent encoded: $link", link.contains("%2E"))
    }

    @Test
    fun `spaces in the shortcut name use percent twenty and never a plus`() {
        val link = ShortcutDistribution.importDeepLink()
        assertTrue(link.contains("&name=Send%20Token%20to%20TV"))
        assertFalse("a plus cannot appear in a form-style encoded value: $link", link.contains("+"))
    }

    @Test
    fun `html attribute escapes the parameter separator`() {
        assertEquals(
            ShortcutDistribution.importDeepLink().replace("&", "&amp;"),
            ShortcutDistribution.importDeepLinkHtml(),
        )
    }
}
