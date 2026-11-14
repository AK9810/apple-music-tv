package com.applemusicktv.util

/**
 * Which sign-in instructions the port-8080 page shows, derived from the request User-Agent.
 *
 * iOS has no Chrome DevTools, so an iPhone that lands on the desktop page cannot follow its
 * steps at all — it needs the Shortcut path instead. The companion marker is checked first
 * and wins: its WebView sends a desktop Chrome UA plus the marker, and it drives the
 * automated sign-in, so it must never be mistaken for "some other browser".
 *
 * The mapping deliberately recognises only the mobile iOS tokens (`iPhone`, `iPad`). An
 * iPad with "Request Desktop Website" on sends a Macintosh UA with no iPad token and maps
 * to [Audience.Other] — the same instructions a Mac user gets, including the DevTools route,
 * which that UA can actually follow on the iPad (Safari's Web Inspector over a Mac). A Mac
 * heuristic here would instead send real desktops to the iOS branch, the worse mistake.
 */
object PageAudience {

    enum class Audience { Companion, Ios, Other }

    private const val COMPANION_MARKER = "AMTVCompanion"

    /**
     * [userAgent] is the raw `User-Agent` header; null or empty means a client that did not
     * send one, which gets the instructions every fallback still supports ([Audience.Other]).
     */
    fun of(userAgent: String?): Audience {
        val ua = userAgent.orEmpty()
        return when {
            ua.contains(COMPANION_MARKER) -> Audience.Companion
            ua.contains("iPhone") || ua.contains("iPad") -> Audience.Ios
            else -> Audience.Other
        }
    }
}
