package com.applemusicktv.util

import java.net.URLDecoder

/**
 * Field lookup for `application/x-www-form-urlencoded` bodies and query strings — the
 * `mut=…&offset=…` shape the port-8080 page, the Android companion and (later) the iOS
 * Shortcut all send.
 *
 * A literal `+` is preserved as `+`, never decoded to a space. The MUT is the only field
 * where a silent mutation is dangerous: a corrupted token looks like "signed in but the
 * library is empty" on the TV, which is very hard for a user to diagnose. Every existing
 * channel already sends `+` percent-encoded (`%2B`) — a browser form escapes a literal
 * plus, and the companion uses `URLEncoder.encode` — so this changes nothing for them.
 * No other field is free text either: they are numbers, a flag, an IP or the token, so
 * no legitimate value relies on `+`-means-space.
 */
object FormBody {

    /**
     * Value of [name] in [encoded] (a form body or a query string), or "" when absent.
     * Malformed percent escapes count as absent.
     */
    fun field(encoded: String, name: String): String {
        val raw = encoded.split("&").find { it.startsWith("$name=") }?.removePrefix("$name=")
            ?: return ""
        return try {
            // URLDecoder itself turns "+" into a space, so re-escape plus signs first.
            URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
        } catch (_: IllegalArgumentException) {
            "" // "%ZZ" and friends: behave like a missing field, never a half-decoded token
        }
    }
}
