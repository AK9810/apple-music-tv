package com.applemusicktv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one-tap import link that the iOS side of the :8080 page shows.
 *
 * iOS refuses `http://` import targets, so the primary action must point at the
 * https copy of the signed artifact. Two ways to get this wrong are invisible on
 * a desktop: double-encoding the nested URL (the Shortcuts app then cannot fetch
 * the file) and encoding spaces as `+` in the `name` parameter (the imported
 * shortcut is named `Send+Token+to+TV`). Both are asserted literally here.
 */
class ShortcutDistributionTest {

    @Test
    fun `the published url is the v1_7 release asset`() {
        assertEquals(
            "https://github.com/AK9810/apple-music-tv/releases/download/v1.7/" +
                "Send-Token-to-TV.shortcut",
            ShortcutDistribution.HTTPS_URL,
        )
    }

    @Test
    fun `deep link percent encodes the https url exactly once`() {
        assertEquals(
            "shortcuts://import-shortcut?" +
                "url=https%3A%2F%2Fgithub%2Ecom%2FAK9810%2Fapple-music-tv%2Freleases%2F" +
                "download%2Fv1%2E7%2FSend-Token-to-TV%2Eshortcut" +
                "&name=Send%20Token%20to%20TV",
            ShortcutDistribution.importDeepLink(),
        )
    }

    @Test
    fun `the nested url is not double encoded and its bare form does not survive`() {
        val link = ShortcutDistribution.importDeepLink()
        assertFalse("scheme got encoded twice: $link", link.contains("%253A"))
        assertFalse("a bare https url survived: $link", link.contains("https://"))
        assertFalse("an unescaped dot survived: $link", link.contains("github.com"))
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
            "shortcuts://import-shortcut?" +
                "url=https%3A%2F%2Fgithub%2Ecom%2FAK9810%2Fapple-music-tv%2Freleases%2F" +
                "download%2Fv1%2E7%2FSend-Token-to-TV%2Eshortcut" +
                "&amp;name=Send%20Token%20to%20TV",
            ShortcutDistribution.importDeepLinkHtml(),
        )
    }
}
