# Apple Music TV — v1.4

- Everything from the 1.3/1.4 roadmap lands together: a bilingual UI, big memory and smoothness wins on low-RAM TVs, and the streaming pipeline overhaul (256 kbps tier, 8× faster cold start).

*🌐 Languages*
English and Simplified Chinese UI — switch in Settings → Language or during onboarding; the choice is remembered per app.
All menus, settings, lyrics and dialogs are translated; Apple's content (album/song titles, editorial shelves) stays in English.

*🎵 Streaming & Audio (proxy server)*
The 256 kbps high-quality tier actually plays now — an asset-priority bug had quietly served the 64 kbps HE-AAC stream instead.
Lossless detection is by real codec (ffprobe), not a bitrate guess, so only genuine ALAC/FLAC gets transcoded.
Byte-range manifests are sliced locally instead of re-downloading the same file up to 17×.
Streaming works behind a system proxy (decryptor no longer inherits and breaks on local MITM proxies).
Cold start cut ~8× (≈20 s → 2.5–4 s): segments are fetched and decrypted in parallel with the manifest work.

*⚡ Performance (2 GB TVs)*
Memory peak during browse/playback trimmed and recomposition churn reduced — measurably smoother scrolling and playback on Fire TV Stick-class hardware.

*🔄 Updates*
The in-app updater now tracks this repo's releases (the upstream stopped publishing).
Version tags (e.g. `v1.4`) automatically publish a stable release with the APK; `main` keeps publishing the rolling dev build.

*🐛 Fixes*
CI builds green again on current runners (setup-android v4).
