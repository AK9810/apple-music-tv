package com.applemusicktv.util

import com.applemusicktv.util.PageAudience.Audience
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the User-Agent → page-audience mapping that decides which sign-in instructions the
 * :8080 page shows. A silent misroute sends iPhone users to Chrome DevTools, a path that
 * does not exist on iOS — they cannot recover from it on the phone, so this is the one
 * branch in the page worth testing.
 */
class PageAudienceTest {

    private data class Case(val label: String, val ua: String?, val expected: Audience)

    @Test
    fun `maps the user agents that decide the sign-in branch`() {
        val cases = listOf(
            Case(
                "iPhone Safari",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
                Audience.Ios,
            ),
            Case(
                // iPad Safari before "Request Desktop Website" is switched on — the mobile
                // UA is the only one that carries the iPad token.
                "iPad Safari",
                "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
                Audience.Ios,
            ),
            Case(
                // The companion WebView: desktop Chrome with the marker appended.
                "companion marked user agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 AMTVCompanion/1.0",
                Audience.Companion,
            ),
            Case(
                "desktop browser",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
                Audience.Other,
            ),
            Case(
                // Documented limitation: an iPad in desktop mode sends a Macintosh UA with
                // no iPad token. It gets the desktop instructions, which that UA can follow.
                "iPad in desktop mode",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                    "(KHTML, like Gecko) Version/17.5 Safari/605.1.15",
                Audience.Other,
            ),
            Case("empty user agent", "", Audience.Other),
            Case("missing user agent", null, Audience.Other),
        )
        for (c in cases) assertEquals(c.label, c.expected, PageAudience.of(c.ua))
    }

    @Test
    fun `the companion marker wins over an iOS user agent`() {
        // The real companion UA is desktop Chrome, but the marker must keep priority on
        // principle: that WebView drives the automated sign-in, so mistaking it for iOS
        // would show manual Shortcut steps where a one-tap button belongs.
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 AMTVCompanion/1.0"
        assertEquals(Audience.Companion, PageAudience.of(ua))
    }
}
