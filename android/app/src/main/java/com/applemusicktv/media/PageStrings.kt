package com.applemusicktv.media

/**
 * Copy for the :8080 control page, one complete set per language.
 *
 * Deliberately not Android string resources: the page is rendered by a hand-rolled HTTP
 * server that has no Context-based resource plumbing in this path, and an interface makes a
 * missing translation in either direction a **compile error** rather than an English string
 * leaking into the Chinese page.
 *
 * Product terms stay untranslated in Chinese copy — `Music-User-Token`, `Token`, `App`,
 * `Wi-Fi` — because that is what the Apple documentation and the TV UI already say; the
 * internal vocabulary (MUT, 录入, 交接) lives in GLOSSARY.md and never reaches the page.
 */
interface PageStrings {
    /** Label of the *other* language on the switch at the top right. */
    val otherLangLabel: String

    // Tabs
    val tabSignIn: String
    val tabAndroid: String
    val tabIos: String
    val tabSettings: String

    // Always-visible status row
    val tStatus: String
    val statusActive: String
    val statusNone: String
    val statusNotSet: String
    fun savedBanner(len: Int): String
    val rejectedBanner: String

    // Tab 1 — sign in (manual paste)
    val tSetToken: String
    val pasteIntro: String
    val pastePlaceholder: String
    val btnSave: String
    val btnClear: String
    val tHowTo: String
    val guideSteps: List<String>
    val iosNoDevtools: String
    val companionSignInBody: String
    val companionSignInBtn: String

    // Tab 2 — Android companion
    val tAndroid: String
    val androidIntro: String
    val androidSteps: List<String>
    val androidAddress: String
    val androidDownload: String
    val androidFaq: String
    val androidFaqItems: List<String>
    val androidQr: String

    // Tab 3 — iOS Shortcut
    val tIos: String
    val iosIntro: String
    val iosInstall: String
    val iosInstallNote: String
    val iosOffline: String
    val iosOfflineBtn: String
    val iosOfflineNote: String
    val iosAfter: String
    fun iosStepAddress(url: String): String
    val iosSteps: List<String>
    val iosPrereq: String
    val iosPrereqItems: List<String>
    val iosFaq: String
    val iosFaqItems: List<String>
    val iosQr: String

    // Tab 4 — settings & diagnostics
    val tLyrics: String
    val currentOffset: String
    val setOffset: String
    val lyricsHelp: String
    val tBeat: String
    val currentLatency: String
    val applyReset: String
    val beatHelp: String
    val tComputer: String
    val current: String
    val notSet: String
    val setComputerIp: String
    val computerHelp: String
    val tStandalone: String
    val status: String
    val standaloneOn: String
    val standaloneOff: String
    val turnOn: String
    val turnOff: String
    val standaloneHelp: String
    val tVolume: String
    val volumeOn: String
    val volumeOff: String
    val volumeHelp: String
    val tCrossfade: String
    val currentLength: String
    val setCrossfade: String
    val crossfadeHelp: String
    val tBug: String
    val lastCrash: String
    val crashOnFile: String
    val crashNone: String
    val downloadReport: String
    val clearCrash: String
    val bugHelp: String
    val tNet: String
    val tAppLogs: String
    val copy: String
    val copied: String
    val copyFailed: String
    val noLogs: String
    val noRequests: String
}

object PageStringsEn : PageStrings {
    override val otherLangLabel = "中文"

    override val tabSignIn = "Sign in"
    override val tabAndroid = "Android companion"
    override val tabIos = "iOS Shortcut"
    override val tabSettings = "Settings & diagnostics"

    override val tStatus = "Status"
    override val statusActive = "Music-User-Token active"
    override val statusNone = "No token"
    override val statusNotSet = "Not set — paste below"
    override fun savedBanner(len: Int) =
        "Token received ($len chars). The TV is picking it up now — no need to touch the remote."
    override val rejectedBanner = "That token was too short to be a Music-User-Token — nothing was saved."

    override val tSetToken = "Set Token"
    override val pasteIntro =
        "Paste your Music-User-Token here. Works in any browser and is the fallback for every other method."
    override val pastePlaceholder = "eyJra… paste your Music-User-Token here"
    override val btnSave = "Save Token"
    override val btnClear = "Clear Token"
    override val tHowTo = "How to get your token"
    override val guideSteps = listOf(
        "Open <strong style=color:#ccc>music.apple.com</strong> in Chrome · sign in",
        "DevTools (F12) → Network tab",
        "Play any song",
        "Find the request to <code>amp-api-edge.music.apple.com</code>",
        "Copy the <code>Media-User-Token</code> request header value",
    )
    override val iosNoDevtools =
        "Chrome DevTools does not exist on iOS — use the <strong style=color:#ccc>iOS Shortcut</strong> tab. The paste box above still works as the last resort."
    override val companionSignInBody =
        "Sign in with your Apple ID (password and 2FA are painless here). Once you're signed in, the app sends the token to the TV on its own and reloads this page — then just go back to the TV."
    override val companionSignInBtn = "Open music.apple.com &amp; sign in"

    override val tAndroid = "Android companion app"
    override val androidIntro =
        "AMTV Companion signs in to Apple Music inside its own WebView and hands the token to this TV automatically. No computer needed."
    override val androidSteps = listOf(
        "Download the companion APK below and install it (Android 7+; the first install needs \"unknown sources\" allowed).",
        "Open the app, type the TV address shown below and connect.",
        "Sign in to Apple Music there — the token comes back on its own and the TV continues by itself.",
    )
    override val androidAddress = "TV address"
    override val androidDownload = "Download the companion APK"
    override val androidFaq = "If no token arrives"
    override val androidFaqItems = listOf(
        "The phone and the TV must be on the same Wi-Fi, and this app must stay open on the TV.",
        "Installed but nothing came back? Confirm you are signed in at music.apple.com inside the companion, then reopen it.",
        "Still stuck: paste the token manually on the first tab — that path works in every browser.",
    )
    override val androidQr = "Scan with the Android phone"

    override val tIos = "iOS / iPadOS Shortcut"
    override val iosIntro =
        "A signed Shortcut reads the token from your own signed-in Safari session and hands it to the TV over the local network. No iOS app, no developer account, no re-signing."
    override val iosInstall = "Install the Shortcut"
    override val iosInstallNote =
        "Opens the import preview in the Shortcuts app. iOS still asks you to tap <strong style=color:#ccc>Add</strong> — that confirmation cannot be skipped."
    override val iosOffline =
        "Offline install — if the phone cannot reach the internet right now, download the identical signed file from this TV:"
    override val iosOfflineBtn = "Download the Shortcut from this TV"
    override val iosOfflineNote =
        "Same artifact as the one-tap install: open it in <strong style=color:#ccc>Files</strong> and tap <strong style=color:#ccc>Add</strong>."
    override val iosAfter = "After it is added"
    override fun iosStepAddress(url: String) =
        "Open the Shortcut's Text action and replace the TV address with <span style=\"font-family:monospace;color:#aaa\">$url</span>."
    override val iosSteps = listOf(
        "Sign in to <strong style=color:#ccc>music.apple.com</strong> in <strong style=color:#ccc>Safari</strong>.",
        "On that page tap <strong style=color:#ccc>Share</strong> → <strong style=color:#ccc>Send Token to TV</strong> and let it run. It reports whether the TV accepted the token.",
    )
    override val iosPrereq = "Three prerequisites, once"
    override val iosPrereqItems = listOf(
        "<strong style=color:#ccc>Allow Running Scripts</strong> — Settings → Shortcuts → Advanced → Allow Running Scripts = on.",
        "<strong style=color:#ccc>Share-sheet availability</strong> — in the Shortcut's details, Show in Share Sheet = on for Safari web pages. Only Safari works; Chrome and Firefox do not hand the page over.",
        "<strong style=color:#ccc>Local Network permission</strong> — the first run asks to find devices on your local network; tap Allow.",
    )
    override val iosFaq = "If the Shortcut does not work"
    override val iosFaqItems = listOf(
        "<strong style=color:#ccc>Paste the token instead</strong> — the Set Token box on the first tab works in any browser and is the last resort for every path.",
        "<strong style=color:#ccc>No token on the page</strong> — sign in at music.apple.com in Safari, then run the Shortcut from that page's share sheet.",
        "<strong style=color:#ccc>TV unreachable</strong> — check that the phone and this TV are on the same network, and that the address inside the Shortcut matches the one at the top of this page.",
        "<strong style=color:#ccc>Worked for months, then stopped</strong> — the token lasts roughly six months; sign in again and re-run the Shortcut.",
    )
    override val iosQr = "Scan with the iPhone"

    override val tLyrics = "Lyrics Offset"
    override val currentOffset = "Current offset"
    override val setOffset = "Set Offset (ms)"
    override val lyricsHelp = "Positive = lyrics show earlier. Negative = later. Try +200 if lyrics feel late."
    override val tBeat = "Beat Latency"
    override val currentLatency = "Current latency"
    override val applyReset = "Apply &amp; Reset"
    override val beatHelp = "0 = TV speakers. ~200 = typical Bluetooth. Resets the beat buffer on apply."
    override val tComputer = "Computer (proxy server)"
    override val current = "Current"
    override val notSet = "not set"
    override val setComputerIp = "Set Computer IP"
    override val computerHelp = "Port 3000 is assumed. Needed for browse, library, search and lyrics. Leave blank to use the built-in default."
    override val tStandalone = "Standalone mode (no PC)"
    override val status = "Status"
    override val standaloneOn = "ON — everything on the TV"
    override val standaloneOff = "OFF — using the PC server"
    override val turnOn = "Turn ON"
    override val turnOff = "Turn OFF"
    override val standaloneHelp = "Talks to Apple directly for browse, library, search, artwork, lyrics AND playback — the PC is not needed at all. Songs start in ~1s instead of 15-20s. Trade-off: some tracks have segment gaps the PC remux repairs, so audio can chop. Applies from the next song."
    override val tVolume = "Volume Leveling"
    override val volumeOn = "ON — evening out loudness"
    override val volumeOff = "OFF — each track at its own level"
    override val volumeHelp = "Slow automatic gain so quiet masters come up and loud ones come down. Takes effect immediately. Watch it work in the App Logs (VOL lines)."
    override val tCrossfade = "Crossfade"
    override val currentLength = "Current length"
    override val setCrossfade = "Set Crossfade"
    override val crossfadeHelp = "Applies from the next song — a fade already running keeps its length. Longer fades need the next track prefetched earlier."
    override val tBug = "Bug Report"
    override val lastCrash = "Last crash"
    override val crashOnFile = "crash on file"
    override val crashNone = "none"
    override val downloadReport = "Download Bug Report"
    override val clearCrash = "Clear crash log"
    override val bugHelp = "Bundles app version, device, the last crash and recent app + network logs into one text file to share."
    override val tNet = "Network Activity"
    override val tAppLogs = "App Logs"
    override val copy = "Copy"
    override val copied = "Copied!"
    override val copyFailed = "Copy failed"
    override val noLogs = "No logs yet."
    override val noRequests = "No requests yet."
}

object PageStringsZh : PageStrings {
    override val otherLangLabel = "EN"

    override val tabSignIn = "登录"
    override val tabAndroid = "Android 伴侣 App"
    override val tabIos = "iOS 快捷指令"
    override val tabSettings = "设置与诊断"

    override val tStatus = "状态"
    override val statusActive = "Music-User-Token 已生效"
    override val statusNone = "没有 Token"
    override val statusNotSet = "未设置 —— 在下方粘贴"
    override fun savedBanner(len: Int) =
        "已收到 Token（$len 字符）。电视正在接收，不需要再碰遥控器。"
    override val rejectedBanner = "这段内容太短，不是有效的 Music-User-Token —— 没有保存。"

    override val tSetToken = "设置 Token"
    override val pasteIntro = "在这里粘贴 Music-User-Token。任何浏览器都能用，也是其它两种方式的兜底。"
    override val pastePlaceholder = "eyJra… 在这里粘贴 Music-User-Token"
    override val btnSave = "保存 Token"
    override val btnClear = "清除 Token"
    override val tHowTo = "如何获取 Token"
    override val guideSteps = listOf(
        "用 Chrome 打开 <strong style=color:#ccc>music.apple.com</strong> 并登录",
        "打开开发者工具（F12）→ Network",
        "随便播放一首歌",
        "找到发往 <code>amp-api-edge.music.apple.com</code> 的请求",
        "复制请求头里的 <code>Media-User-Token</code> 值",
    )
    override val iosNoDevtools =
        "iOS 上没有 Chrome 开发者工具 —— 请用 <strong style=color:#ccc>「iOS 快捷指令」</strong>页签。上面的粘贴框仍然是最后的兜底。"
    override val companionSignInBody =
        "在这里用 Apple ID 登录（密码和双重认证都很顺畅）。登录完成后，App 会自动把 Token 交给电视并刷新本页 —— 然后回到电视即可。"
    override val companionSignInBtn = "打开 music.apple.com 并登录"

    override val tAndroid = "Android 伴侣 App"
    override val androidIntro = "AMTV Companion 在自己内置的浏览器里完成 Apple Music 登录，然后把 Token 自动交给这台电视。不需要电脑。"
    override val androidSteps = listOf(
        "下载下方 APK 并安装（Android 7 以上；首次安装需要允许「未知来源」）。",
        "打开 App，输入下方显示的电视地址并连接。",
        "在 App 里登录 Apple Music —— Token 会自动回传，电视会自己继续。",
    )
    override val androidAddress = "电视地址"
    override val androidDownload = "下载伴侣 App（APK）"
    override val androidFaq = "如果一直没收到 Token"
    override val androidFaqItems = listOf(
        "手机和电视必须在同一 Wi-Fi 下，并且电视上的这个 App 要保持打开。",
        "装好了但没回传？先确认伴侣 App 里已经登录 music.apple.com，然后重新打开一次。",
        "还是不行：回第一个页签手动粘贴 —— 那条路在任何浏览器都能用。",
    )
    override val androidQr = "用安卓手机扫描"

    override val tIos = "iOS / iPadOS 快捷指令"
    override val iosIntro = "一个已签名的快捷指令会从你已登录的 Safari 会话里取出 Token，经局域网交给电视。不需要 iOS app，不需要开发者账号，也不用反复重签。"
    override val iosInstall = "安装快捷指令"
    override val iosInstallNote = "会打开快捷指令 App 的导入预览。iOS 仍会要求点一次 <strong style=color:#ccc>「添加」</strong> —— 这个确认无法跳过。"
    override val iosOffline = "离线安装 —— 如果手机此刻没有网络，可以从这台电视下载同一个已签名文件："
    override val iosOfflineBtn = "从电视下载快捷指令"
    override val iosOfflineNote = "与一键安装是同一个文件：在 <strong style=color:#ccc>「文件」</strong>里打开，点 <strong style=color:#ccc>「添加」</strong>。"
    override val iosAfter = "添加之后"
    override fun iosStepAddress(url: String) =
        "打开快捷指令里的「文本」动作，把电视地址换成 <span style=\"font-family:monospace;color:#aaa\">$url</span>。"
    override val iosSteps = listOf(
        "在 <strong style=color:#ccc>Safari</strong> 里登录 <strong style=color:#ccc>music.apple.com</strong>。",
        "在该页面点 <strong style=color:#ccc>「分享」</strong> → <strong style=color:#ccc>「Send Token to TV」</strong> 运行，它会告诉你电视是否收下了 Token。",
    )
    override val iosPrereq = "三个前置设置（只需一次）"
    override val iosPrereqItems = listOf(
        "<strong style=color:#ccc>允许运行脚本</strong> —— 设置 → 快捷指令 → 高级 → 允许运行脚本 = 开。",
        "<strong style=color:#ccc>加入分享表单</strong> —— 在快捷指令的详情里，为 Safari 网页打开「在分享表单中显示」。只有 Safari 可以，Chrome 与 Firefox 不会把页面交给它。",
        "<strong style=color:#ccc>本地网络权限</strong> —— 第一次运行会询问「查找本地网络设备」，点允许。",
    )
    override val iosFaq = "快捷指令不工作时"
    override val iosFaqItems = listOf(
        "<strong style=color:#ccc>改用粘贴</strong> —— 第一个页签的粘贴框在任何浏览器都能用，是所有方式的最终兜底。",
        "<strong style=color:#ccc>页面上取不到 Token</strong> —— 确认是在 Safari 里登录的 music.apple.com，并从该页面分享运行。",
        "<strong style=color:#ccc>电视连不上</strong> —— 确认手机和电视在同一网络，且快捷指令里的地址与本页顶部的地址一致。",
        "<strong style=color:#ccc>用了几个月突然失效</strong> —— Token 大约半年过期，重新登录并再跑一次即可。",
    )
    override val iosQr = "用 iPhone 扫描"

    override val tLyrics = "歌词偏移"
    override val currentOffset = "当前偏移"
    override val setOffset = "设置偏移（毫秒）"
    override val lyricsHelp = "正数 = 歌词提前显示，负数 = 延后。觉得歌词慢了就试 +200。"
    override val tBeat = "节拍延迟"
    override val currentLatency = "当前延迟"
    override val applyReset = "应用并重置"
    override val beatHelp = "0 = 电视扬声器；约 200 = 常见蓝牙。应用时会重置节拍缓冲。"
    override val tComputer = "电脑（代理服务器）"
    override val current = "当前"
    override val notSet = "未设置"
    override val setComputerIp = "设置电脑 IP"
    override val computerHelp = "默认端口 3000。浏览、资料库、搜索与歌词都依赖它。留空使用内置默认值。"
    override val tStandalone = "独立模式（不需要电脑）"
    override val status = "状态"
    override val standaloneOn = "开启 —— 一切都在电视上"
    override val standaloneOff = "关闭 —— 使用电脑端服务器"
    override val turnOn = "开启"
    override val turnOff = "关闭"
    override val standaloneHelp = "浏览、资料库、搜索、封面、歌词和播放全部直连 Apple，完全不需要电脑；歌曲约 1 秒起播，而不是 15–20 秒。代价：部分曲目有分段缝隙，电脑端的 remux 会修掉它们，所以直连时可能听到断续。下一首起生效。"
    override val tVolume = "音量均衡"
    override val volumeOn = "开启 —— 拉平响度"
    override val volumeOff = "关闭 —— 每首保持自己的音量"
    override val volumeHelp = "缓慢的自动增益：安静的母带抬上来，响的压下去。立即生效。应用日志里的 VOL 行能看到它工作。"
    override val tCrossfade = "交叉淡入淡出"
    override val currentLength = "当前长度"
    override val setCrossfade = "设置淡入淡出"
    override val crossfadeHelp = "下一首起生效；正在进行的淡变保持原长度。更长的淡出需要更早预取下一首。"
    override val tBug = "故障报告"
    override val lastCrash = "最近崩溃"
    override val crashOnFile = "有崩溃记录"
    override val crashNone = "无"
    override val downloadReport = "下载故障报告"
    override val clearCrash = "清除崩溃日志"
    override val bugHelp = "把 App 版本、设备、最近一次崩溃，以及近期应用与网络日志打包成一个文本文件，便于分享。"
    override val tNet = "网络活动"
    override val tAppLogs = "应用日志"
    override val copy = "复制"
    override val copied = "已复制！"
    override val copyFailed = "复制失败"
    override val noLogs = "暂无日志。"
    override val noRequests = "暂无请求。"
}
