# Apple Music TV — v1.8

- iOS joins token setup: a signed Shortcut captures the MUT from your own signed-in Safari and hands it to the TV over the LAN — no app, no developer account. Both sign-in surfaces were rebuilt around the three intake methods, and the control page now speaks English and 简体中文.

*📱 Token setup from an iPhone or iPad*
The TV now serves the signed `Send Token to TV` Shortcut itself (`http://<tv-ip>:8080/send-token-to-tv.shortcut`): download it, open it in Files and add it, set the TV address once, then run it from music.apple.com's share sheet. The phone page detects iPhone/iPad — including iPad in desktop mode — and swaps the impossible Chrome DevTools steps for the Shortcut flow; the Android companion and manual paste are unchanged. Why a Shortcut instead of an app is ADR-0002; why the one-tap `shortcuts://import-shortcut` link was dropped in favour of the TV download is ADR-0004. Verified end-to-end on a real iPhone and a real iPad against a real TV.

*🪟 Sign-in, in tabs*
The TV onboarding's sign-in step (which overflowed the 1080p content box by ~50dp) is now three focus-select tabs — browser paste / companion app / iOS·iPadOS — each with one QR code, a one-line summary and a typeable address. The :8080 control page is four CSS tabs (Sign in / Android companion / iOS Shortcut / Settings & diagnostics) with the default tab chosen per visitor, deep-linkable `?tab=` and a green banner when a token lands. The phone-facing tabs gained a one-tap "copy the TV address" button.

*🌐 Bilingual control page*
The whole :8080 page speaks English and Simplified Chinese: `?lang=zh|en` wins, else the browser's `Accept-Language` primary tag, else English. QR codes render server-side as SVG, and only on desktop browsers — a phone has nothing to scan them with.

*🔧 Fixes & hardening*
Finishing setup no longer drops you into Home scrolled to wherever you left it: the first Home after 开始聆听 starts at the first row (#18). Token form-body parsing is extracted and pinned by tests, so a literal `+` in a token survives (#11), and the Shortcut reports "no token on this page" instead of a false success when Safari isn't signed in (#13).

*📄 Docs*
`docs/ios-shortcut.md` (+ its Chinese twin) walks a fresh phone to a playing library song. The `shortcut/` directory ships the build recipe, unsigned source, signed artifact and the verification record. SECURITY.md no longer describes a bookmarklet that never existed, and ADR-0002/0003/0004 record the three decisions behind this release.
