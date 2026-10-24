# Apple Music TV — v1.6

- One-tap sign-in: the new **AMTV Companion** app on your phone signs in to Apple Music and delivers the token to the TV by itself — no more copying long tokens.

*📱 Two ways to sign in*
Setup step 2 now lays out both routes. **Method 1 (original):** open the TV's :8080 page in a phone browser and paste your Music-User-Token. **Method 2 (new):** install the AMTV Companion APK — the TV serves it itself (`http://<TV>:8080/companion.apk`, QR + address on the sign-in screen) — pair it once with the TV's address, sign in inside its WebView, and the token lands on the TV; the setup flow advances on its own.

*🤝 How the companion works*
The companion is a minimal WebView shell: it opens music.apple.com with a desktop identity so you get the full web player, then captures the token **natively** — from the cookie jar and from the `media-user-token` request header your player already sends — and hands it to the TV over your LAN. Nothing is injected into Apple's pages, so there is nothing to break when Apple redesigns. The Apple session lives only in the companion's private storage on your phone; uninstalling the app clears it.

*🧹 Removed*
The bookmarklet ("bookmark script") token route — its in-page extraction never worked reliably. The manual paste box and the DevTools token guide remain as the no-app fallback.

*🔧 Under the hood*
`MutPreferences` now exposes the token as a StateFlow, so the onboarding screen reacts the instant a token arrives. The token sync to the PC proxy is fire-and-forget: a sleeping PC no longer turns a successful sign-in into an error. The :8080 status page logs token saves, and the companion logs its capture/delivery under the `AMTVCompanion` tag for diagnosis.
