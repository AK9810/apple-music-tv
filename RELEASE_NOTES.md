# Apple Music TV — v1.7

- A render-performance release for low-powered TVs: the focus glow is gone, page transitions went from 700ms to 200ms, Now Playing artwork cuts in instead of cross-fading, and the ambient background is capped at 30fps.

*⚡ Focus: glow → outline*
Cards and transport controls no longer draw tv-material's blurred `glow` shadow — every focused card was running a real `setShadowLayer` blur each frame. Focus is now a 1.5dp white outline plus a subtle 1.05–1.08 scale, closer to Netflix / Google TV; compact transport buttons keep the filled, slightly larger look.

*🚀 Navigation is 200ms*
Full-screen page changes used navigation-compose's default 700ms crossfade — the most stutter-prone moment in the app. They're now a 200ms fade with an 8dp vertical shift. Now Playing's mode switch (player / full-screen lyrics / screensaver) drops from 450ms to 250ms.

*🎨 Now Playing*
Artwork switches with a hard cut: the old double crossfade (Compose + Coil, 1s each) is gone, and the mood is carried by the 1.5s background tint animation instead. The ambient gradient background renders at 30fps instead of 60 — the drift cycles last 15–34s, so it looks the same while halving the full-screen draw cost.

*🔧 Under the hood*
Every duration now comes from one `MotionBudget` object (focus 100–160ms, navigation 200ms, ambient 30fps). The numbers are a budget measured on the baseline device, so an animation that exceeds them counts as a regression rather than a taste decision. Lazy grids get `contentType` for better recycling, the Now Playing EqBars read their animation values in the draw phase instead of recomposing, and Compose compiler stability reports are generated in the build. The Dev menu gained a frame-time overlay with per-frame stats for on-device comparison.

*📄 Docs*
The rationale is written up in `docs/adr/0001-tv-motion-and-render-principles.md`, with the Compose stability baseline and the perf roadmap next to it in `docs/performance/`.
