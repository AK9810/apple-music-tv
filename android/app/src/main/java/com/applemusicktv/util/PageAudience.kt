package com.applemusicktv.util

/**
 * Which sign-in instructions the port-8080 page shows, derived from the request User-Agent.
 *
 * iOS has no Chrome DevTools, so an iPhone that lands on the desktop page cannot follow its
 * steps at all — it needs the Shortcut path instead. The companion marker is checked first
 * and wins: its WebView sends a desktop Chrome UA plus the marker, and it drives the
 * automated sign-in, so it must never be mistaken for "some other browser".
 *
 * The mapping recognises the mobile iOS tokens (`iPhone`, `iPad`). Default iPadOS Safari
 * sends a `Macintosh` UA, and the server sees only that header — it cannot tell the tablet
 * from a real Mac. The touchscreen is visible only inside the page, so the page checks
 * `navigator.maxTouchPoints` and reloads once as `/?ios=1`; [iosRequested] carries that
 * hint here. It is checked after the marker and before the UA, which keeps a real Mac (no
 * touch points, never redirected) on the desktop branch.
 */
object PageAudience {

    enum class Audience { CompanionApp, Ios, Other }

    private const val COMPANION_MARKER = "AMTVCompanion"

    /**
     * [userAgent] is the raw `User-Agent` header; null or empty means a client that did not
     * send one, which gets the instructions every fallback still supports ([Audience.Other]).
     * [iosRequested] is the page's `/?ios=1` hint, which only an iPad in desktop mode sends.
     */
    fun of(userAgent: String?, iosRequested: Boolean = false): Audience {
        val ua = userAgent.orEmpty()
        return when {
            ua.contains(COMPANION_MARKER) -> Audience.CompanionApp
            iosRequested -> Audience.Ios
            ua.contains("iPhone") || ua.contains("iPad") -> Audience.Ios
            else -> Audience.Other
        }
    }

    /**
     * Whether the request comes from a phone or tablet rather than a desktop. Only used to
     * decide if the control page should render "scan this" QRs: the client is already on the
     * device that would do the scanning, so a QR there is noise.
     */
    fun isMobileBrowser(userAgent: String?): Boolean {
        val ua = userAgent.orEmpty()
        return ua.contains("Android") || ua.contains("Mobile") ||
            ua.contains("iPhone") || ua.contains("iPad")
    }
}
