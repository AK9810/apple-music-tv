package com.applemusicktv.media

import android.content.Context
import android.net.wifi.WifiManager
import com.applemusicktv.data.CrossfadePreferences
import com.applemusicktv.data.ServerPreferences
import com.applemusicktv.data.StandalonePreferences
import com.applemusicktv.data.LyricsOffsetPreferences
import com.applemusicktv.data.MutPreferences
import com.applemusicktv.data.NetworkLog
import com.applemusicktv.data.repository.MusicRepository
import com.applemusicktv.util.FormBody
import com.applemusicktv.util.PageAudience
import com.applemusicktv.util.PageLang
import com.applemusicktv.util.QrCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InAppWebServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: MutPreferences,
    private val repo: MusicRepository,
    private val lyricsOffsetPrefs: LyricsOffsetPreferences,
    private val crossfadePrefs: CrossfadePreferences,
    private val standalonePrefs: StandalonePreferences,
    private val serverPrefs: ServerPreferences,
    private val beatAnalyzer: BeatAnalyzer,
) {
    private val logs = ArrayDeque<String>(300)
    private val sseClients = java.util.concurrent.CopyOnWriteArrayList<java.io.OutputStream>()
    // Port 8001: live event stream for curl
    private val eventClients = java.util.concurrent.CopyOnWriteArrayList<java.io.OutputStream>()
    private var job: Job? = null
    private var eventJob: Job? = null
    private var appScope: CoroutineScope? = null
    private var serverSocket: ServerSocket? = null
    private var eventSocket: ServerSocket? = null
    val port = 8080
    val eventPort = 8081

    private val wsPrefs = context.getSharedPreferences("webserver_prefs", Context.MODE_PRIVATE)
    /** User toggle (Dev → Web Server). Default ON. */
    val isEnabled: Boolean get() = wsPrefs.getBoolean("web_enabled", true)
    val running: Boolean get() = job?.isActive == true

    /** Called once at app start: remember the scope and start only if enabled. */
    fun boot(scope: CoroutineScope) {
        appScope = scope
        if (isEnabled) start(scope)
    }

    /** Toggle from Settings — starts or stops the phone web server and persists the choice. */
    fun setEnabled(on: Boolean) {
        wsPrefs.edit().putBoolean("web_enabled", on).apply()
        if (on) appScope?.let { start(it) } else stop()
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        appScope = scope
        // Stream network-request lines live too, so :8081 and the web page show ALL logs, not just app logs.
        NetworkLog.listener = { line -> broadcastLive(line) }
        job = scope.launch(Dispatchers.IO) {
            val server = try { ServerSocket(port).also { serverSocket = it } } catch (_: Exception) { return@launch }
            while (isActive) {
                try { val s = server.accept(); launch { handle(s) } } catch (_: Exception) { if (!isActive) break }
            }
            try { server.close() } catch (_: Exception) {}
        }
        eventJob = scope.launch(Dispatchers.IO) {
            val server = try { ServerSocket(eventPort).also { eventSocket = it } } catch (e: Exception) {
                addLog("WARN", "Event port $eventPort in use — kill previous instance and restart")
                return@launch
            }
            while (isActive) {
                try { val s = server.accept(); launch { handleEvent(s) } } catch (_: Exception) { if (!isActive) break }
            }
            try { server.close() } catch (_: Exception) {}
        }
        addLog("OK", "Web server started — open ${serverUrl()} on your phone")
        val ip = serverUrl().removePrefix("http://").substringBefore(":")
        addLog("OK", "Event stream: curl http://$ip:$eventPort")
    }

    // Close the sockets too — cancelling the job alone leaves accept() blocked and the port bound.
    fun stop() {
        job?.cancel(); eventJob?.cancel()
        runCatching { serverSocket?.close() }; serverSocket = null
        runCatching { eventSocket?.close() }; eventSocket = null
        addLog("OK", "Web server stopped")
    }

    // Shared formatter (SimpleDateFormat is not thread-safe — guard it). addLog runs many times
    // per second during playback; a new formatter each call was pure garbage on the audio path.
    private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun addLog(level: String, msg: String) {
        val t = synchronized(logTimeFormat) { logTimeFormat.format(Date()) }
        val line = "[$t][$level] $msg"
        synchronized(logs) {
            if (logs.size >= 300) logs.removeFirst()
            logs.addLast(line)
        }
        broadcastLive(line)
        // Debug mirror to the PC proxy console. SKIP when the proxy isn't reachable —
        // otherwise every log line blocks an IO thread up to connectTimeout (1s) trying
        // to reach a dead host. In standalone-primary mode the app logs constantly during
        // playback, which saturated the IO dispatcher and starved Coil image decode →
        // stuttery menu scroll. The :8081 SSE stream still carries logs live.
        if (!serverPrefs.serverReachable) return
        appScope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val body = """{"level":"$level","msg":${org.json.JSONObject.quote(msg)}}"""
                val conn = java.net.URL("${repo.serverBaseUrl()}/api/log")
                    .openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 1000
                conn.readTimeout = 1000
                conn.outputStream.write(body.toByteArray())
                conn.responseCode
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    /** Push one line to every live listener — SSE (web page) and the raw :8081 event stream. */
    private fun broadcastLive(line: String) {
        val sseData = "data: ${line.replace("\n", " ")}\n\n".toByteArray()
        val deadSse = mutableListOf<java.io.OutputStream>()
        for (out in sseClients) { try { out.write(sseData); out.flush() } catch (_: Exception) { deadSse.add(out) } }
        sseClients.removeAll(deadSse)
        val eventLine = "${line.replace("\n", " ")}\n".toByteArray()
        appScope?.launch(kotlinx.coroutines.Dispatchers.IO) {
            val dead = mutableListOf<java.io.OutputStream>()
            for (out in eventClients) { try { out.write(eventLine); out.flush() } catch (_: Exception) { dead.add(out) } }
            eventClients.removeAll(dead)
        }
    }

    fun getLogs(): List<String> = synchronized(logs) { logs.toList() }

    fun serverUrl(): String = "http://${lanIp()}:$port"

    /**
     * The TV's LAN IP — the QR code and every "open this on your phone" hint are built from it.
     *
     * The old probe (WifiManager.connectionInfo) reports 0.0.0.0 on Ethernet-connected boxes and
     * on emulators, so walk every interface instead: prefer wlan/eth addresses in home subnets,
     * and keep the Wi-Fi probe only as a last resort.
     */
    private fun lanIp(): String {
        val best = runCatching {
            val enum = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            val scored = mutableListOf<Pair<Int, String>>()
            for (nif in enum) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    val ip = (addr as? Inet4Address)?.hostAddress ?: continue
                    if (addr.isLoopbackAddress || ip.startsWith("169.254.")) continue
                    var score = when {
                        nif.name.startsWith("wlan") -> 40
                        nif.name.startsWith("eth") -> 30
                        else -> 10
                    }
                    when {
                        ip.startsWith("192.168.") -> score += 3
                        ip.startsWith("10.") -> score += 2
                        ip.startsWith("172.") -> score += 1
                    }
                    scored.add(score to ip)
                }
            }
            scored.maxByOrNull { it.first }?.second
        }.getOrNull()
        if (best != null) return best
        // Legacy probe — some TVs only expose their address here.
        val wm = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wm.connectionInfo.ipAddress
        return if (ip != 0) "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}" else "0.0.0.0"
    }

    private fun handle(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = BufferedOutputStream(socket.getOutputStream())
            val reqLine = reader.readLine() ?: return
            val parts = reqLine.split(" ")
            val method = parts.getOrNull(0) ?: "GET"
            val rawPath = parts.getOrNull(1) ?: "/"
            val path = rawPath.substringBefore('?')
            val query = rawPath.substringAfter('?', "")
            val hdrs = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (!line.isNullOrBlank()) {
                val idx = line.indexOf(": ")
                if (idx > 0) hdrs[line.substring(0, idx).lowercase()] = line.substring(idx + 2)
                line = reader.readLine()
            }
            var body = ""
            if (method == "POST") {
                val len = hdrs["content-length"]?.toIntOrNull() ?: 0
                val buf = CharArray(len); reader.read(buf, 0, len); body = String(buf)
            }
            when {
                // `?ios=1` is the page's own hint for an iPad whose default desktop-mode UA
                // says Macintosh; the server cannot see the touchscreen behind that header.
                method == "GET"  && path == "/"            -> send(out, 200, "text/html", html(
                    audience = PageAudience.of(hdrs["user-agent"], iosRequested = FormBody.field(query, "ios") == "1"),
                    lang = PageLang.resolve(FormBody.field(query, "lang"), hdrs["accept-language"]),
                    tabParam = FormBody.field(query, "tab").toIntOrNull()?.takeIf { it in 1..4 },
                    saved = FormBody.field(query, "saved") == "1",
                    rejected = FormBody.field(query, "error") == "1",
                    mobile = PageAudience.isMobileBrowser(hdrs["user-agent"]),
                ))
                method == "GET"  && path == "/status"      -> send(out, 200, "application/json", status())
                method == "GET"  && path == "/logs"        -> send(out, 200, "application/json", logsJson())
                method == "GET"  && path == "/stream"      -> { handleSse(socket, out); return }

                method == "GET"  && path == "/netlogs"     -> send(out, 200, "application/json", netLogsJson())
                method == "GET"  && path == "/report"      -> send(out, 200, "text/plain", bugReport(), "Content-Disposition: attachment; filename=\"amtv-bugreport.txt\"\r\n")
                method == "GET"  && path == "/companion.apk" -> sendAsset(
                    out = out,
                    asset = "companion.apk",
                    contentType = "application/vnd.android.package-archive",
                    filename = "AMTVCompanion.apk",
                    missing = "Companion APK is not bundled in this build",
                )
                // The signed iOS Shortcut, for an install that needs no internet. Its asset
                // name is the ASCII copy the Gradle task writes; the download filename keeps
                // the spaces because the file name becomes the Shortcut's name on import.
                method == "GET"  && path == "/send-token-to-tv.shortcut" -> sendAsset(
                    out = out,
                    asset = "send-token-to-tv.shortcut",
                    contentType = "application/octet-stream",
                    filename = "Send Token to TV.shortcut",
                    missing = "The iOS Shortcut is not bundled in this build",
                )
                // Top-level-navigation hand-off from the extraction script. The script runs inside
                // Apple's HTTPS origin, where a fetch to a plain-HTTP LAN address is mixed-content
                // blocked — only a full page navigation carries the token over (design: 2026-10-24).
                // One landing for every hand-off (form POST, companion GET, Shortcut POST):
                // the sign-in tab with a banner. A phone that navigates here mid-flow would
                // otherwise lose the confirmation — and on iOS the default tab is not the
                // sign-in one, so the redirect has to say `tab=1` explicitly.
                method == "GET"  && path == "/set-token"   -> {
                    val ok = applyToken(FormBody.field(query, "mut"))
                    redirect(out, if (ok) "/?tab=1&saved=1" else "/?tab=1&error=1")
                }
                method == "POST" && path == "/clear-crash"        -> { com.applemusicktv.util.CrashReporter.clear(context); addLog("OK", "Crash log cleared"); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-token"          -> {
                    val ok = applyToken(FormBody.field(body, "mut"))
                    redirect(out, if (ok) "/?tab=1&saved=1" else "/?tab=1&error=1")
                }
                method == "POST" && path == "/clear-token"         -> { prefs.setMUT(""); addLog("WARN","Token cleared"); redirect(out, "/?tab=1") }
                // Settings forms land back on their own tab: they are edited in a row, and a
                // redirect to "/" would drop the user on the sign-in tab after every save.
                method == "POST" && path == "/set-lyrics-offset"   -> { applyLyricsOffset(FormBody.field(body, "offset")); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-beat-latency"    -> { applyBeatLatency(FormBody.field(body, "latency")); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-crossfade"       -> { applyCrossfade(FormBody.field(body, "crossfade")); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-standalone"      -> { applyStandalone(FormBody.field(body, "standalone")); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-server-ip"       -> { applyServerIp(FormBody.field(body, "serverip")); redirect(out, "/?tab=4") }
                method == "POST" && path == "/set-volume-leveling" -> { applyVolumeLeveling(FormBody.field(body, "vol")); redirect(out, "/?tab=4") }
                else -> send(out, 404, "text/plain", "Not found")
            }
            out.flush(); socket.close()
        } catch (_: Exception) {}
    }

    private fun applyBeatLatency(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null || ms < 0) { addLog("ERROR", "Invalid beat latency: $raw"); return }
        beatAnalyzer.latencyMs = ms
        beatAnalyzer.resetBeat()
        addLog("OK", "Beat latency set to ${ms}ms — buffer reset")
    }

    /** Player settings live in the "player_state" prefs the ViewModel owns. */
    private fun playerPrefs() = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)

    private fun applyVolumeLeveling(raw: String) {
        val on = raw.trim() == "1"
        GainProcessor.enabled = on   // takes effect on the audio thread immediately
        playerPrefs().edit().putBoolean("volume_leveling", on).apply()
        addLog("OK", "Volume leveling ${if (on) "ON" else "OFF"}")
    }

    private fun applyServerIp(raw: String) {
        val ip = raw.trim()
        serverPrefs.setPcServerIp(ip)
        addLog("OK", if (ip.isEmpty()) "Proxy IP cleared — using the built-in default"
                     else "Proxy IP set to $ip")
    }

    private fun applyStandalone(raw: String) {
        val on = raw.trim() == "1" || raw.trim().equals("true", ignoreCase = true)
        standalonePrefs.setEnabled(on)
        addLog("OK", "Standalone playback ${if (on) "ON" else "OFF"} — applies from next song")
    }

    private fun applyCrossfade(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null) { addLog("ERROR", "Invalid crossfade: $raw"); return }
        crossfadePrefs.setDuration(ms)
        addLog("OK", "Crossfade set to ${crossfadePrefs.getDuration()}ms — applies from next song")
    }

    private fun applyLyricsOffset(raw: String) {
        val ms = raw.trim().toLongOrNull()
        if (ms == null) { addLog("ERROR", "Invalid offset: $raw"); return }
        lyricsOffsetPrefs.setOffset(ms)
        addLog("OK", "Lyrics offset set to ${ms}ms")
    }

    /** Stores the token and mirrors it to the proxy server; false means nothing was saved. */
    private fun applyToken(mut: String): Boolean {
        if (mut.length < 20) { addLog("ERROR", "Token too short"); return false }
        prefs.setMUT(mut)
        addLog("OK", "Music-User-Token saved (${mut.length} chars)")
        appScope?.launch {
            runCatching { repo.syncMUTToServer(mut) }.onFailure { addLog("WARN", "Server MUT sync failed: ${it.message}") }
        }
        return true
    }

    private fun handleEvent(socket: Socket) {
        try {
            socket.soTimeout = 0
            socket.tcpNoDelay = true
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()
            // Drain HTTP request headers
            var line = reader.readLine()
            while (!line.isNullOrBlank()) { line = reader.readLine() }
            val header = "HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
            out.write(header.toByteArray()); out.flush()
            // Dump EVERYTHING on connect: network request log + full app log backlog,
            // then keep streaming live app logs. `curl http://<firetv-ip>:8081`.
            out.write("===== NETWORK REQUESTS =====\n".toByteArray())
            for (n in NetworkLog.getAll()) { out.write("${n}\n".toByteArray()) }
            out.write("===== APP LOG =====\n".toByteArray())
            val backlog = synchronized(logs) { logs.toList() }
            for (l in backlog) { out.write("${l}\n".toByteArray()) }
            out.write("===== LIVE =====\n".toByteArray()); out.flush()
            eventClients.add(out)
            try { while (!socket.isClosed) Thread.sleep(500) } catch (_: Exception) {}
            eventClients.remove(out)
        } catch (_: Exception) {}
        try { socket.close() } catch (_: Exception) {}
    }

    private fun handleSse(socket: Socket, out: BufferedOutputStream) {
        val header = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\nConnection: keep-alive\r\nAccess-Control-Allow-Origin: *\r\n\r\n"
        out.write(header.toByteArray()); out.flush()
        sseClients.add(out)
        try { while (!socket.isClosed) Thread.sleep(1000) } catch (_: Exception) {}
        sseClients.remove(out)
        try { socket.close() } catch (_: Exception) {}
    }

    private fun send(out: BufferedOutputStream, code: Int, type: String, body: String, extra: String = "") {
        val bytes = body.toByteArray()
        val status = mapOf(200 to "OK", 302 to "Found", 303 to "See Other", 400 to "Bad Request", 404 to "Not Found")[code] ?: "Error"
        val resp = "HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n$extra\r\n"
        out.write(resp.toByteArray()); out.write(bytes)
    }

    private fun redirect(out: BufferedOutputStream, to: String) = send(out, 303, "text/plain", "", "Location: $to\r\n")

    /**
     * Serve a binary asset bundled by a Gradle copy task (see app/build.gradle.kts) — the
     * companion APK and the signed iOS Shortcut. send() is text-only, hence this byte
     * variant; the exact Content-Length and Connection: close matter to Safari's download
     * handling, so both callers share one header path. A missing asset is a 404 in the
     * caller's own words.
     */
    private fun sendAsset(
        out: BufferedOutputStream,
        asset: String,
        contentType: String,
        filename: String,
        missing: String,
    ) {
        val bytes = try { context.assets.open(asset).use { it.readBytes() } }
        catch (_: Exception) { send(out, 404, "text/plain", missing); return }
        val resp = "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\nConnection: close\r\n" +
            "Content-Disposition: attachment; filename=\"$filename\"\r\n\r\n"
        out.write(resp.toByteArray()); out.write(bytes)
    }

    /** A single self-contained text dump for bug reports: environment + crash + logs. */
    private fun bugReport(): String {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val crash = com.applemusicktv.util.CrashReporter.lastCrash(context)
        return buildString {
            appendLine("===== Apple Music TV — bug report =====")
            appendLine("Generated: $ts")
            appendLine("App: v${com.applemusicktv.BuildConfig.VERSION_NAME} (${com.applemusicktv.BuildConfig.VERSION_CODE})")
            appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            appendLine("Standalone: ${standalonePrefs.isEnabled()} · Token set: ${prefs.hasMUT()}")
            appendLine()
            appendLine("===== LAST CRASH =====")
            appendLine(crash ?: "(none recorded)")
            appendLine()
            appendLine("===== NETWORK REQUESTS =====")
            NetworkLog.getAll().forEach { appendLine(it) }
            appendLine()
            appendLine("===== APP LOG =====")
            getLogs().forEach { appendLine(it) }
        }
    }

    private fun status() = """{"hasMUT":${prefs.hasMUT()},"mutLen":${prefs.getMUT().length},"url":"${serverUrl()}","hasCrash":${com.applemusicktv.util.CrashReporter.lastCrash(context) != null}}"""
    private fun logsJson() = "[${getLogs().joinToString(",") { "\"${it.replace("\"","'")}\"" }}]"
    private fun netLogsJson() = "[${NetworkLog.getAll().joinToString(",") { "\"${it.replace("\"","'")}\"" }}]"

    /** Escape a value that reaches the page as HTML text or an attribute. */
    private fun esc(v: String) = v
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    /** Escape a value that reaches a JS string literal. */
    private fun js(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    /**
     * Server-side QR for the desktop branches, where the phone that should scan it is a
     * different device. Reuses the app's own encoder (byte mode, EC-M, versions 1–6 — 106
     * bytes max); the URLs here are 20–50 bytes. Returns "" when the text does not fit, so a
     * future longer URL degrades to "no QR" instead of taking the page down.
     */
    private fun qrSvg(text: String, side: Int = 132): String {
        val m = QrCode.encode(text) ?: return ""
        val n = m.size
        val path = buildString {
            for (r in 0 until n) for (c in 0 until n) if (m[r][c]) {
                append('M').append(c).append(' ').append(r).append("h1v1h-1z")
            }
        }
        // The four-module quiet zone lives in the viewBox: scanners that reject a code
        // bleeding into its background are the usual "why won't it scan" case. Both fills
        // must be quoted — in an unquoted attribute the self-closing slash becomes part of
        // the value (`#fff/`), the colour is invalid, and the whole code renders black.
        return """<div class=qrbox><svg viewBox="-4 -4 ${n + 8} ${n + 8}" width=$side height=$side shape-rendering=crispEdges>""" +
            """<rect x=-4 y=-4 width=${n + 8} height=${n + 8} fill="#fff"/><path d="$path" fill="#000"/></svg></div>"""
    }

    /**
     * The :8080 control page: four tabs — sign in, Android companion, iOS Shortcut, settings —
     * built from hidden radios and `:checked`, so switching never reloads and the log polling
     * keeps running. [audience] picks the default tab and the companion/iOS wording; [lang]
     * picks one of the two complete copy sets in [PageStrings].
     *
     * `?tab=` is the deep link and what the post-save redirects use. `?saved=1` / `?error=1`
     * render above the tabs, not inside one: the confirmation must survive a default tab that
     * points elsewhere (an iOS visitor defaults to the Shortcut tab).
     *
     * QRs render only for a desktop browser — a phone already on the page has nothing to scan.
     */
    private fun html(
        audience: PageAudience.Audience,
        lang: PageLang,
        tabParam: Int?,
        saved: Boolean,
        rejected: Boolean,
        mobile: Boolean,
    ): String {
        val s: PageStrings = if (lang == PageLang.ZH) PageStringsZh else PageStringsEn
        val has = prefs.hasMUT()
        val preview = if (has) esc(prefs.getMUT().take(32) + "…") else ""
        val currentOffset = lyricsOffsetPrefs.getOffset()
        val currentBeatLatency = beatAnalyzer.latencyMs
        val standaloneOn = standalonePrefs.isEnabled()
        val serverIp = esc(serverPrefs.getPcServerIp())
        val pp = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val volLevelOn = pp.getBoolean("volume_leveling", false)
        val currentCrossfade = crossfadePrefs.getDuration()
        val crossfadeSec = "%.1f".format(currentCrossfade / 1000f)
        val hasCrash = com.applemusicktv.util.CrashReporter.lastCrash(context) != null
        val logRows = getLogs().reversed().take(80).joinToString("") { log ->
            val cls = when { log.contains("[OK]") -> "g"; log.contains("[ERROR]") -> "r"; log.contains("[WARN]") -> "y"; else -> "d" }
            "<div class=$cls>${esc(log)}</div>"
        }.ifEmpty { "<div class=d>${s.noLogs}</div>" }
        val netRows = NetworkLog.getAll().reversed().take(100).joinToString("") { log ->
            val cls = when { log.contains("✓") -> "g"; log.contains("✗") -> "r"; else -> "d" }
            "<div class=$cls>${esc(log)}</div>"
        }.ifEmpty { "<div class=d>${s.noRequests}</div>" }

        val activeTab = tabParam ?: when {
            saved || rejected -> 1
            audience == PageAudience.Audience.Ios -> 3
            audience == PageAudience.Audience.CompanionApp -> 1
            mobile -> 2
            else -> 1
        }
        val showQr = audience == PageAudience.Audience.Other && !mobile
        fun qrBlock(url: String, caption: String): String =
            if (!showQr) "" else "<div class=qrcol>${qrSvg(url)}<div class=qrcap>$caption</div></div>"
        fun radio(id: Int): String =
            "<input type=radio name=tab id=t$id value=$id${if (activeTab == id) " checked" else ""}>"
        fun list(items: List<String>): String = items.joinToString("") { "<li>$it</li>" }

        val banner = when {
            saved && has -> "<div class=banner>${esc(s.savedBanner(prefs.getMUT().length))}</div>"
            rejected -> "<div class=banner bad>${esc(s.rejectedBanner)}</div>"
            else -> ""
        }
        // Default iPadOS Safari sends a Macintosh UA, so the server cannot tell it from a
        // real Mac; only the page sees the touchscreen. Emitted on the Other branch alone,
        // and a redirected load carries ios=1, so the redirect cannot loop.
        val ipadDesktopRedirect = if (audience == PageAudience.Audience.Other) """
if (navigator.maxTouchPoints > 1 && navigator.userAgent.indexOf('Macintosh') !== -1 &&
    new URLSearchParams(location.search).get('ios') !== '1') {
  var ipadUrl = new URL(location.href); ipadUrl.searchParams.set('ios', '1'); location.replace(ipadUrl.toString());
}
""" else ""

        return """<!DOCTYPE html><html lang=${lang.htmlLang}><head><meta charset=UTF-8><meta name=viewport content="width=device-width,initial-scale=1">
<title>Apple Music TV</title><style>
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;background:#0a0a0a;color:#e5e5e7;padding:20px;max-width:680px;margin:auto}
header{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:14px}
h1{font-size:20px;font-weight:700;margin-bottom:2px}
.sub{font-size:11px;color:#444;font-family:monospace;word-break:break-all}
.lang{font-size:12px;color:#aaa;text-decoration:none;border:1px solid #2c2c2e;border-radius:999px;padding:8px 14px;white-space:nowrap}
.card{background:#111;border-radius:12px;padding:16px;margin-bottom:12px}
h2{font-size:10px;font-weight:600;color:#555;text-transform:uppercase;letter-spacing:.8px;margin-bottom:12px}
body.zh h2{font-size:12px;text-transform:none;letter-spacing:0;color:#666}
.row{display:flex;align-items:center;gap:10px;margin-bottom:10px}
.dot{width:9px;height:9px;border-radius:50%;flex-shrink:0}
.dot-g{background:#34c759}.dot-r{background:#ff3b30}
.g{color:#34c759}.r{color:#ff3b30}.y{color:#ffd60a}.d{color:#555}
.label{font-size:13px;color:#ccc}
.sub2{font-size:10px;color:#555;font-family:monospace;word-break:break-all;margin-top:2px}
.hint{font-size:12px;color:#999;line-height:1.7;margin-bottom:10px}
.note{font-size:11px;color:#888;line-height:1.6;margin-top:8px}
.help{font-size:10px;color:#555;line-height:1.7;margin-top:8px}
.stephead{font-size:12px;color:#999;margin:16px 0 2px}
.banner{background:#0f2a18;border:1px solid #1f4d2c;color:#6bcb77;border-radius:10px;padding:12px 14px;font-size:13px;line-height:1.6;margin-bottom:12px}
.banner.bad{background:#2a1012;border-color:#4d1f24;color:#ff6b6b}
.tabwrap{position:relative}
.tabwrap input{position:absolute;opacity:0;pointer-events:none}
.tabbar{display:flex;gap:8px;overflow-x:auto;margin-bottom:14px;padding-bottom:2px;scrollbar-width:none}
.tabbar::-webkit-scrollbar{display:none}
.tabbar label{flex:0 0 auto;padding:11px 16px;border-radius:999px;background:#151515;border:1px solid #232323;color:#999;font-size:14px;cursor:pointer;white-space:nowrap;-webkit-tap-highlight-color:transparent}
#t1:checked~.tabbar label[for=t1],#t2:checked~.tabbar label[for=t2],#t3:checked~.tabbar label[for=t3],#t4:checked~.tabbar label[for=t4]{background:#fa233b;border-color:#fa233b;color:#fff}
#t1:focus-visible~.tabbar label[for=t1],#t2:focus-visible~.tabbar label[for=t2],#t3:focus-visible~.tabbar label[for=t3],#t4:focus-visible~.tabbar label[for=t4]{outline:2px solid #fa233b;outline-offset:2px}
.panel{display:none}
#t1:checked~.p1,#t2:checked~.p2,#t3:checked~.p3,#t4:checked~.p4{display:block}
.split{display:flex;gap:16px;align-items:flex-start}
.main{flex:1;min-width:0}
.qrcol{flex:0 0 auto;text-align:center}
.qrbox{background:#fff;border-radius:10px;padding:6px;display:inline-block}
.qrbox svg{display:block;width:132px;height:132px}
.qrcap{font-size:10px;color:#888;margin-top:6px;max-width:144px}
.addr{font-family:monospace;font-size:13px;color:#6bcb77;word-break:break-all;margin:2px 0 10px}
.addrrow{display:flex;align-items:center;gap:10px;margin:2px 0 10px}
.addrrow .addr{margin:0;flex:1;min-width:0}
.copybtn{flex:0 0 auto;background:#1c1c1e;border:1px solid #2c2c2e;color:#ccc;padding:8px 12px;border-radius:8px;font-size:12px;cursor:pointer}
.copybtn:active{opacity:.7}
textarea{width:100%;background:#1c1c1e;border:1.5px solid #2c2c2e;border-radius:8px;color:#e5e5e7;font-family:monospace;font-size:11px;padding:10px;min-height:80px;outline:none;resize:vertical}
textarea:focus{border-color:#fa233b}
.btn{display:block;width:100%;padding:13px;border-radius:8px;font-size:14px;font-weight:600;border:none;cursor:pointer;margin-top:8px;text-align:center}
.btn-p{background:#fa233b;color:#fff}.btn-s{background:#1c1c1e;color:#888}
.btn:active{opacity:.7}
ol{font-size:12px;color:#888;line-height:2;padding-left:18px}
ul{font-size:12px;color:#888;line-height:1.9;padding-left:18px;margin-top:8px}
code{color:#fa233b;font-family:monospace;font-size:11px}
details{margin-top:12px}
summary{font-size:12px;color:#888;cursor:pointer}
.logs{background:#080808;border-radius:8px;padding:10px;max-height:240px;overflow-y:auto;font-family:monospace;font-size:10px;line-height:1.8}
.rbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.rbtn{background:none;border:1px solid #222;color:#555;padding:4px 12px;border-radius:6px;font-size:11px;cursor:pointer}
.rbtn:active{color:#aaa}
@media (min-width:700px){body{padding:28px}}
</style>
<script>
var I18N={active:${js(s.statusActive)},none:${js(s.statusNone)},nologs:${js(s.noLogs)},noreqs:${js(s.noRequests)},copied:${js(s.copied)},failed:${js(s.copyFailed)}};
function renderStatus(h){var el=document.getElementById('status');if(!el)return;el.innerHTML='<div class=row><div class="dot '+(h?'dot-g':'dot-r')+'"></div><div class=label>'+(h?I18N.active:I18N.none)+'</div></div>';}
function refresh(){
  fetch('/status').then(r=>r.json()).then(s=>renderStatus(s.hasMUT));
  fetch('/logs').then(r=>r.json()).then(logs=>{var el=document.getElementById('applogs');if(!el)return;el.innerHTML=logs.reverse().slice(0,80).map(l=>{var c=l.includes('[OK]')?'g':l.includes('[ERROR]')?'r':l.includes('[WARN]')?'y':'d';return'<div class='+c+'>'+l.replace(/</g,'&lt;')+'</div>';}).join('')||'<div class=d>'+I18N.nologs+'</div>';});
  fetch('/netlogs').then(r=>r.json()).then(logs=>{var el=document.getElementById('netlogs');if(!el)return;el.innerHTML=logs.reverse().slice(0,100).map(l=>{var c=l.includes('✓')?'g':l.includes('✗')?'r':'d';return'<div class='+c+'>'+l.replace(/</g,'&lt;')+'</div>';}).join('')||'<div class=d>'+I18N.noreqs+'</div>';});
}
setInterval(refresh,3000);
function copyLogs(id){fetch(id==='applogs'?'/logs':'/netlogs').then(r=>r.json()).then(logs=>{var text=logs.reverse().join('\n');var b=document.getElementById('copy-'+id);var done=function(){var orig=b.textContent;b.textContent=I18N.copied;setTimeout(function(){b.textContent=orig;},1500);};if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text).then(done).catch(function(){legacyCopy(text,done,b);});}else{legacyCopy(text,done,b);}});}
function copyText(id,btnId){
  var el=document.getElementById(id);var b=document.getElementById(btnId);
  if(!el||!b)return;
  var text=el.textContent.trim();
  var done=function(){var orig=b.textContent;b.textContent=I18N.copied;setTimeout(function(){b.textContent=orig;},1500);};
  if(navigator.clipboard&&window.isSecureContext){navigator.clipboard.writeText(text).then(done).catch(function(){legacyCopy(text,done,b);});}else{legacyCopy(text,done,b);}
}
function legacyCopy(text,done,b){var restore=b.textContent;try{var ta=document.createElement('textarea');ta.value=text;ta.style.position='fixed';ta.style.top='0';ta.style.left='0';ta.style.opacity='0';document.body.appendChild(ta);ta.focus();ta.select();var ok=document.execCommand('copy');document.body.removeChild(ta);if(ok){done();}else{b.textContent=I18N.failed;setTimeout(function(){b.textContent=restore;},1500);}}catch(e){b.textContent=I18N.failed;setTimeout(function(){b.textContent=restore;},1500);}}
function goLang(l){var u=new URL(location.href);u.searchParams.set('lang',l);var t=document.querySelector('input[name=tab]:checked');if(t)u.searchParams.set('tab',t.value);location.href=u.toString();return false;}
document.querySelectorAll('input[name=tab]').forEach(function(r){r.addEventListener('change',function(){var u=new URL(location.href);u.searchParams.set('tab',r.value);history.replaceState(null,'',u.toString());});});
$ipadDesktopRedirect
</script>
</head><body class=${if (lang == PageLang.ZH) "zh" else "en"}>
<header>
<div><h1>Apple Music TV</h1><p class=sub>${serverUrl()}</p></div>
<a class=lang href="?lang=${if (lang == PageLang.ZH) "en" else "zh"}" onclick="return goLang('${if (lang == PageLang.ZH) "en" else "zh"}')">${s.otherLangLabel}</a>
</header>

<div class=card>
<h2>${s.tStatus}</h2>
<div id=status>
<div class=row><div class="dot ${if (has) "dot-g" else "dot-r"}"></div><div><div class=label>${if (has) s.statusActive else s.statusNone}</div>${if (has) "<div class=sub2>$preview</div>" else "<div class=sub2>${s.statusNotSet}</div>"}</div></div>
</div>
</div>
${banner}
<div class=tabwrap>
${radio(1)}${radio(2)}${radio(3)}${radio(4)}
<nav class=tabbar>
<label for=t1>${s.tabSignIn}</label>
<label for=t2>${s.tabAndroid}</label>
<label for=t3>${s.tabIos}</label>
<label for=t4>${s.tabSettings}</label>
</nav>

<section class="panel p1">
${if (audience == PageAudience.Audience.CompanionApp) companionCard(s) else ""}
<div class=card>
<h2>${s.tSetToken}</h2>
<p class=hint>${s.pasteIntro}</p>
<form method=POST action=/set-token>
<textarea name=mut placeholder="${esc(s.pastePlaceholder)}"></textarea>
<button class="btn btn-p" type=submit>${s.btnSave}</button>
</form>
${if (has) "<form method=POST action=/clear-token><button class='btn btn-s' type=submit>${s.btnClear}</button></form>" else ""}
</div>
${if (audience == PageAudience.Audience.Ios) """
<div class=card><h2>${s.tabIos}</h2><p class=hint style=margin:0>${s.iosNoDevtools}</p></div>
""" else devtoolsCard(s)}
</section>

<section class="panel p2">
<div class=card>
<h2>${s.tAndroid}</h2>
<div class=split>
<div class=main>
<p class=hint>${s.androidIntro}</p>
<ol>${list(s.androidSteps)}</ol>
<div class=stephead>${s.androidAddress}</div>
<div class=addrrow><div class=addr id=addr-android>${serverUrl()}</div><button class=copybtn type=button id=copy-android onclick="copyText('addr-android','copy-android')">${s.copyAddress}</button></div>
<a class="btn btn-p" href=/companion.apk download style=text-decoration:none>${s.androidDownload}</a>
<details><summary>${s.androidFaq}</summary><ul>${list(s.androidFaqItems)}</ul></details>
</div>
${qrBlock(serverUrl() + "/companion.apk", s.androidQr)}
</div>
</div>
</section>

<section class="panel p3">
<div class=card>
<h2>${s.tIos}</h2>
<div class=split>
<div class=main>
<p class=hint>${s.iosIntro}</p>
<a class="btn btn-p" style=text-decoration:none href=/send-token-to-tv.shortcut download>${s.iosDownload}</a>
<div class=note>${s.iosDownloadNote}</div>
<div class=stephead>${s.iosAddress}</div>
<div class=addrrow><div class=addr id=addr-ios>${serverUrl()}</div><button class=copybtn type=button id=copy-ios onclick="copyText('addr-ios','copy-ios')">${s.copyAddress}</button></div>
<div class=stephead>${s.iosAfter}</div>
<ol>
<li>${s.iosStepAddress}</li>
${list(s.iosSteps)}
</ol>
<div class=stephead>${s.iosPrereq}</div>
<ol>${list(s.iosPrereqItems)}</ol>
<details><summary>${s.iosFaq}</summary><ul>${list(s.iosFaqItems)}</ul></details>
</div>
${qrBlock(serverUrl() + "/?ios=1", s.iosQr)}
</div>
</div>
</section>

<section class="panel p4">
<div class=card>
<h2>${s.tLyrics}</h2>
<div class=row><div class=label>${s.currentOffset}</div><div class=sub2 style=color:#aaa>${currentOffset}ms</div></div>
<form method=POST action=/set-lyrics-offset>
<input name=offset type=number value="$currentOffset" placeholder="0" style="width:100%;background:#1c1c1e;border:1.5px solid #2c2c2e;border-radius:8px;color:#e5e5e7;font-family:monospace;font-size:13px;padding:10px;outline:none;margin-top:4px">
<button class="btn btn-p" type=submit style=margin-top:8px>${s.setOffset}</button>
</form>
<div class=help>${s.lyricsHelp}</div>
</div>

<div class=card>
<h2>${s.tBeat}</h2>
<div class=row><div class=label>${s.currentLatency}</div><div class=sub2 style=color:#aaa>${currentBeatLatency}ms</div></div>
<form method=POST action=/set-beat-latency>
<input name=latency type=range min=0 max=500 step=10 value="$currentBeatLatency" oninput="document.getElementById('bv').textContent=this.value+'ms'" style="width:100%;accent-color:#fa233b;margin-top:8px">
<div style="font-size:12px;color:#aaa;text-align:center;margin-top:4px"><span id=bv>${currentBeatLatency}ms</span></div>
<button class="btn btn-p" type=submit style=margin-top:8px>${s.applyReset}</button>
</form>
<div class=help>${s.beatHelp}</div>
</div>

<div class=card>
<h2>${s.tComputer}</h2>
<div class=row><div class=label>${s.current}</div><div class=sub2 style=color:#aaa>${if (serverIp.isEmpty()) s.notSet else serverIp}</div></div>
<form method=POST action=/set-server-ip>
<input name=serverip type=text inputmode=decimal placeholder="192.168.1.190" value="$serverIp" style="width:100%;box-sizing:border-box;padding:10px;margin-top:8px;border-radius:6px;border:1px solid #333;background:#0d0d0d;color:#fff;font-family:monospace;font-size:15px">
<button class="btn btn-p" type=submit style=margin-top:8px>${s.setComputerIp}</button>
</form>
<div class=help>${s.computerHelp}</div>
</div>

<div class=card>
<h2>${s.tStandalone}</h2>
<div class=row><div class=label>${s.status}</div><div class=sub2 style="color:${if (standaloneOn) "#6bcb77" else "#aaa"}">${if (standaloneOn) s.standaloneOn else s.standaloneOff}</div></div>
<form method=POST action=/set-standalone>
<input type=hidden name=standalone value="${if (standaloneOn) "0" else "1"}">
<button class="btn ${if (standaloneOn) "" else "btn-p"}" type=submit style=margin-top:8px>${if (standaloneOn) s.turnOff else s.turnOn}</button>
</form>
<div class=help>${s.standaloneHelp}</div>
</div>

<div class=card>
<h2>${s.tVolume}</h2>
<div class=row><div class=label>${s.status}</div><div class=sub2 style="color:${if (volLevelOn) "#6bcb77" else "#aaa"}">${if (volLevelOn) s.volumeOn else s.volumeOff}</div></div>
<form method=POST action=/set-volume-leveling>
<input type=hidden name=vol value="${if (volLevelOn) "0" else "1"}">
<button class="btn ${if (volLevelOn) "" else "btn-p"}" type=submit style=margin-top:8px>${if (volLevelOn) s.turnOff else s.turnOn}</button>
</form>
<div class=help>${s.volumeHelp}</div>
</div>

<div class=card>
<h2>${s.tCrossfade}</h2>
<div class=row><div class=label>${s.currentLength}</div><div class=sub2 style=color:#aaa>${crossfadeSec}s</div></div>
<form method=POST action=/set-crossfade>
<input name=crossfade type=range min=${CrossfadePreferences.MIN_MS} max=${CrossfadePreferences.MAX_MS} step=500 value="$currentCrossfade" oninput="document.getElementById('cv').textContent=(this.value/1000).toFixed(1)+'s'" style="width:100%;accent-color:#fa233b;margin-top:8px">
<div style="font-size:12px;color:#aaa;text-align:center;margin-top:4px"><span id=cv>${crossfadeSec}s</span></div>
<button class="btn btn-p" type=submit style=margin-top:8px>${s.setCrossfade}</button>
</form>
<div class=help>${s.crossfadeHelp}</div>
</div>

<div class=card>
<h2>${s.tBug}</h2>
<div class=row><div class=label>${s.lastCrash}</div><div class=sub2 style="color:${if (hasCrash) "#ff3b30" else "#6bcb77"}">${if (hasCrash) s.crashOnFile else s.crashNone}</div></div>
<a class="btn btn-p" href=/report download style=text-decoration:none>${s.downloadReport}</a>
<form method=POST action=/clear-crash><button class="btn btn-s" type=submit>${s.clearCrash}</button></form>
<div class=help>${s.bugHelp}</div>
</div>

<div class=card>
<div class=rbar><h2 style=margin:0>${s.tNet}</h2><div style=display:flex;gap:6px><button class=rbtn id=copy-netlogs onclick="copyLogs('netlogs')">${s.copy}</button><button class=rbtn onclick=refresh()>↻</button></div></div>
<div class=logs id=netlogs>$netRows</div>
</div>

<div class=card>
<div class=rbar><h2 style=margin:0>${s.tAppLogs}</h2><div style=display:flex;gap:6px><button class=rbtn id=copy-applogs onclick="copyLogs('applogs')">${s.copy}</button><button class=rbtn onclick=refresh()>↻</button></div></div>
<div class=logs id=applogs>$logRows</div>
</div>
</section>
</div>
</body></html>"""
    }

    /**
     * The companion app's WebView signature screen: it *is* the Android companion, so this
     * tab opens Apple's sign-in directly instead of explaining how to install itself.
     */
    private fun companionCard(s: PageStrings) = """
<div class=card>
<h2>${s.tabSignIn}</h2>
<p class=hint>${s.companionSignInBody}</p>
<a class="btn btn-p" style=text-decoration:none href="https://music.apple.com/">${s.companionSignInBtn}</a>
</div>
"""

    /** The DevTools walkthrough — only reachable from a desktop browser, by definition. */
    private fun devtoolsCard(s: PageStrings) = """
<div class=card>
<h2>${s.tHowTo}</h2>
<ol>${s.guideSteps.joinToString("") { "<li>$it</li>" }}</ol>
</div>
"""
}
