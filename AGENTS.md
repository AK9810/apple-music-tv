# AGENTS.md

## What this is

Native Apple Music client for Fire TV / Android TV / Google TV (personal, educational; GPL-3.0). Two deployable parts:

- **Android app** — Kotlin, Jetpack Compose for TV, Media3 ExoPlayer, Hilt, Retrofit+Moshi, Coil.
- **Proxy server** (`server/`) — Bun + Hono wrapping Apple Music's web APIs (bearer scraped from music.apple.com, no developer account). Optional; the app also has a pure-Kotlin on-device Widevine path ("standalone").

## Read before editing

- **[CLAUDE.md](CLAUDE.md)** — the densest, most current map of the repo: auth/MUT flow, playback pipeline, server routes, and many hard-won gotchas. Read it before touching playback, auth, streaming, or data fetching.
- [docs/TECHNICAL.md](docs/TECHNICAL.md) — architecture, on-device CENC/Widevine pipeline, CI.
- [CONTRIBUTING.md](CONTRIBUTING.md) — setup and PR expectations.

## Layout

| Path | Contents |
|------|----------|
| `android/app/src/main/java/com/applemusicktv/` | The app: `data/` (datasource/network/repository/DI), `media/` (playback + on-device Widevine decrypt, `AppleDirectClient`), `ui/` (screens/components/viewmodels/navigation), `util/` |
| `server/src/` | Bun+Hono proxy: `routes/` per domain, `auth.ts` (bearer scrape/refresh), `stream.ts` (decrypt + serve) |
| `server/stream_decrypt.py` | CENC decrypt + ffmpeg remux, called by the stream route |
| `token-server/generate-token.js` | Apple MusicKit developer-token helper |
| `.github/workflows/android.yml` | CI: builds debug APK on every push/PR, publishes rolling `dev` pre-release on pushes to `main`, and a stable release when a version tag (`v1.4`, …) is pushed — a `main` push that bumps `versionName` also cuts its stable release automatically, in the same run |

## Build / run

```bash
# Android (JDK 17, compileSdk 35, minSdk 23)
cd android && ./gradlew assembleDebug
adb connect <FIRE_TV_IP> && adb install -r app/build/outputs/apk/debug/app-debug.apk

# Proxy server (needs .env — see server/.env.example or setup-windows.ps1)
cd server && bun install && bun run dev   # --watch mode
```

Verification is mostly on-device (real Fire TV) — the emulator misses D-pad focus, HDCP, and decoder behaviour that matter here. Unit tests are sparse (`QrCodeTest`); CI must stay green on PRs.

## Architecture rules

- **Standalone/proxy parity**: most data fetching exists twice — proxy routes in `server/src/routes/` and on-device mirrors in `DirectMusicDataSource` / `DirectBrowseSource`. Change one → mirror the other. Titles, section order and filtering are deliberately identical; don't "simplify" them apart.
- **ID prefixes decide the endpoint**: library ids (`i.` songs, `l.` albums, `r.` artists, `p.` library playlists) must hit library endpoints; `pl.`/catalog ids hit catalog. Never swap an `i.` id for a catalog id.
- **Playback**: single `PlayerViewModel` hoisted in `AppShell`; ExoPlayer used directly (one media item at a time, our own queue + auto-advance). Crossfade, prefetch (N+1/N+2), and standalone-stutter fallback logic are subtle — see CLAUDE.md "Playback" before changing queue/advance code.
- **ffmpeg flags in `stream_decrypt.py` are load-bearing** (`aresample=async=1` repairs fMP4 segment gaps; never add `-fflags +genpts`/`+igndts` — causes audible warble). Don't touch without reading CLAUDE.md's stream notes.

## Conventions & gotchas

- Gitignored, never commit: `server/.env`, `server/auth-state.json`, `android/local.properties`, `*.keystore`.
- `android/local.properties` carries `proxyBaseUrl` → compiled into `PROXY_BASE_URL`; overridable at runtime (Dev menu).
- Fire TV perf budget (Now Playing `DynamicBackground`): max 4 gradient blobs, no `Modifier.blur()`, no extra draw passes — all measured to lag. `TvLazyVerticalGrid` doesn't exist in tv-material 1.0.0 (use `LazyVerticalGrid`).
- UI prefs (crossfade, beat latency, lyrics offset…) live in prefs classes exposing `StateFlow` so the :8080 phone page edits apply live — a getter-only prefs class silently needs an app restart.
- Product decisions to preserve: MUT is entered only via the phone web server (port 8080), no in-app token text field; don't auto-focus text fields on load.
- Style: small, well-commented changes over broad rewrites; match surrounding code.
