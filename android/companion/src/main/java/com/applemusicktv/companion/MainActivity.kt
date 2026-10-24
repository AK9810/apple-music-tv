package com.applemusicktv.companion

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.InputType
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * A two-screen WebView shell around the TV's :8080 page (native-capture hand-off, 2026-10-24).
 *
 * First launch asks for the TV address once; after that the shell just loads it. The TV page
 * recognizes the UA marker and shows a "Sign in to Apple Music" button that navigates this
 * WebView to music.apple.com. Nothing is ever injected into Apple's pages: the token is
 * captured natively, the way Capsulyric/AMDL/Orchard do it — CookieManager sits below the page
 * sandbox so `media-user-token` is readable no matter how Apple sets it, and
 * shouldInterceptRequest sees the same header the signed-in player stamps on every
 * api.music.apple.com call. Delivery is a plain native GET to the TV's /set-token, so the
 * mixed-content rules that break in-page fetches don't apply. The injected-script predecessor
 * of this scheme failed exactly where this one can't: all its probes lived inside Apple's page
 * and died silently in the WebView (see the v1.6 postmortem).
 */
class MainActivity : Activity() {

    private val prefs by lazy { getSharedPreferences("companion", MODE_PRIVATE) }
    private var webView: WebView? = null
    private var tvBase: String? = null
    private var captureThread: HandlerThread? = null

    // Written from the WebView's network thread (header sniff) and our poll thread (cookie
    // jar); only the poll thread delivers, so plain volatile fields are enough.
    @Volatile private var capturedToken: String? = null
    @Volatile private var deliveredToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val base = prefs.getString(KEY_TV_BASE, null)
        if (base == null) showPairing() else showWeb(base)
    }

    /** One-time setup: the TV's :8080 address, e.g. 192.168.1.4. */
    private fun showPairing(prefill: String = "") {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val title = TextView(this).apply {
            text = "Apple Music TV — Companion"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val hint = TextView(this).apply {
            text = "Connect to the same Wi-Fi as the TV, then enter the address shown on the " +
                "TV's setup screen (or Settings → Dev menu), e.g. 192.168.1.4"
            textSize = 13f
            setTextColor(0xFF999999.toInt())
        }
        val input = EditText(this).apply {
            // `this.` — the outer local `hint` TextView would otherwise shadow the EditText property.
            this.hint = "TV address"
            setText(prefill)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val error = TextView(this).apply { setTextColor(0xFFFF3B30.toInt()); textSize = 12f }
        val button = Button(this).apply {
            text = "Connect"
            setOnClickListener {
                val url = normalize(input.text.toString())
                if (url == null) error.text = "That doesn't look like an address — try 192.168.1.4"
                else {
                    prefs.edit().putString(KEY_TV_BASE, url).apply()
                    showWeb(url)
                }
            }
        }
        val match = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContentView(LinearLayout(this).apply {
            // targetSdk 35 forces edge-to-edge on Android 15+ phones — without this the
            // title sits under the status bar.
            fitsSystemWindows = true
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF0A0A0A.toInt())
            addView(title, match); addView(hint, match)
            addView(input, match); addView(button, match); addView(error, match)
        })
    }

    private fun showWeb(base: String) {
        tvBase = base
        val wv = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // Desktop Chrome UA: Apple serves Android-WebView UAs a downgraded mobile page
                // where the token never materializes. The AMTVCompanion marker must survive —
                // the TV page keys on it (a plain contains check) to show the sign-in button.
                userAgentString = DESKTOP_UA
                // Apple's desktop layout is cramped on a phone-width screen; allow pinch-zoom
                // so the sign-in controls are reachable (zoom buttons stay invisible).
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    // Apple sprouts intent:// / itms-apps:// links; only real web URLs belong here.
                    val scheme = req.url.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    val host = req.url.host ?: return null
                    if (host == "api.music.apple.com" || host == "amp-api.music.apple.com") {
                        for ((name, value) in req.requestHeaders) {
                            if (name.equals("media-user-token", true) && value.length > 20) {
                                capture(value)
                                break
                            }
                        }
                    }
                    return null
                }

                override fun onReceivedError(view: WebView, req: WebResourceRequest, err: WebResourceError) {
                    // Unreachable TV (typo, Wi-Fi switch) — back to pairing, value kept for editing.
                    if (req.isForMainFrame) runOnUiThread { showPairing(base) }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    // The injected-script scheme failed invisibly; keep Apple's console in
                    // logcat so any future regression has a paper trail.
                    Log.d(TAG, "console: ${m.message()} (${m.sourceId()}:${m.lineNumber()})")
                    return true
                }
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Apple's sign-in crosses idmsa.apple.com → music.apple.com; without third-party
            // cookies the session never lands in this jar (Capsulyric ships the same line).
            setAcceptThirdPartyCookies(wv, true)
        }
        // USB-only remote debugging of this WebView — same CDP surface used to verify the
        // capture loop below (Network.setCookie) and to diagnose field reports.
        WebView.setWebContentsDebuggingEnabled(true)
        // Same edge-to-edge story as the pairing screen: the WebView would otherwise render
        // both the TV page and Apple's page under the status bar on Android 15+ phones.
        val root = FrameLayout(this).apply { fitsSystemWindows = true }
        root.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = wv
        setContentView(root)
        startCapturePolling()
        wv.loadUrl(base)
    }

    /** One background loop for the whole capture+deliver cycle, ticking every POLL_MS. */
    private fun startCapturePolling() {
        if (captureThread != null) return
        val thread = HandlerThread(TAG).apply { start() }
        captureThread = thread
        Handler(thread.looper).post(object : Runnable {
            override fun run() {
                pollTick()
                Handler(thread.looper).postDelayed(this, POLL_MS)
            }
        })
    }

    private fun pollTick() {
        val base = tvBase ?: return
        if (capturedToken == null) parseCookieToken()?.let { capture(it) }
        val token = capturedToken ?: return
        if (token == deliveredToken) return
        if (deliver(base, token)) {
            deliveredToken = token
            Log.d(TAG, "token delivered — returning to the TV page")
            runOnUiThread {
                Toast.makeText(this, "Token sent to the TV", Toast.LENGTH_SHORT).show()
                webView?.loadUrl(base)
            }
        }
    }

    /** Both capture channels converge here; idempotent, delivery happens on the poll thread. */
    @Synchronized private fun capture(token: String) {
        if (token == capturedToken || token == deliveredToken) return
        Log.d(TAG, "token captured (${token.length} chars)")
        capturedToken = token
    }

    /** media-user-token straight from the native cookie jar — readable even if the page can't. */
    private fun parseCookieToken(): String? {
        val cookie = CookieManager.getInstance().getCookie("https://music.apple.com") ?: return null
        for (pair in cookie.split(';')) {
            val p = pair.trim()
            if (p.startsWith("media-user-token=", ignoreCase = true)) {
                return p.substringAfter('=').trim().takeIf { it.length > 20 }
            }
        }
        return null
    }

    /** Native GET to the TV's /set-token — no page involved, so no mixed-content limits. */
    private fun deliver(base: String, token: String): Boolean = try {
        val conn = URL("$base/set-token?mut=${URLEncoder.encode(token, "UTF-8")}")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        val code = conn.responseCode
        if (code >= 400) conn.errorStream?.close() else conn.inputStream?.close()
        conn.disconnect()
        (code in 200..299).also { if (!it) Log.w(TAG, "TV answered HTTP $code") }
    } catch (e: Exception) {
        Log.w(TAG, "hand-off to $base failed: $e")
        false
    }

    /** Accept "192.168.1.4", "192.168.1.4:8080" or a full URL; default the port to 8080. */
    private fun normalize(raw: String): String? {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return null
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
        val host = s.removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (!host.matches(Regex("[A-Za-z0-9._-]+(:\\d+)?"))) return null
        val withPort = if (host.contains(':')) host else "$host:8080"
        return "http://$withPort"
    }

    override fun onBackPressed() {
        val wv = webView
        if (wv != null && wv.canGoBack()) wv.goBack() else super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        // The Apple session lives in this app's cookie jar — flush it so it survives the
        // process dying, exactly like the TV app does for its own jar.
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        captureThread?.quitSafely()
        captureThread = null
        super.onDestroy()
    }

    private companion object {
        const val KEY_TV_BASE = "tv_base"
        const val TAG = "AMTVCompanion"
        const val POLL_MS = 1500L

        // Desktop Chrome with the companion marker appended — see showWeb for why both
        // halves matter. Keep the marker in sync with InAppWebServer's UA check.
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/126.0.0.0 Safari/537.36 AMTVCompanion/1.0"
    }
}
