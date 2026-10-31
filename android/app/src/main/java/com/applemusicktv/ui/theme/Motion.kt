package com.applemusicktv.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's one and only motion budget (ADR-0001 §2: 导航动效 vs 氛围动效).
 *
 * Every animation duration/offset should be read from here instead of being inlined at the call
 * site: the numbers are a *budget* measured on the baseline device (雷鸟 FF 55R69A), and a motion
 * that exceeds them is treated as a performance regression, not a taste choice.
 *
 * Units: `*Ms` are milliseconds; [pageTransitionOffset] is a Compose dp; [ambientFps] is frames
 * per second. This object is immutable (`val`s only) — don't turn it into mutable prefs.
 */
object MotionBudget {

    // ── 导航动效 / navigation motion ─────────────────────────────────────────────────────────
    // Everything the user drives with the D-pad must feel immediate; this tier is the cheap one.

    /** Target focus animation length, in ms (ADR-0001 §3: focus motion budget is 100–160ms). */
    val focusMs: Int = 100

    /** Upper bound for focus animations that need to settle visually, in ms. */
    val focusSettleMs: Int = 160

    /** Full-screen page transition length, in ms (ADR-0001 §4: 200ms, replacing the 700ms default). */
    val pageTransitionMs: Int = 200

    /** Vertical displacement that accompanies a page transition, in dp (ADR-0001 §4: 8dp). */
    val pageTransitionOffset: Dp = 8.dp

    /** Now Playing mode switch (player / full-screen lyrics / screensaver), in ms (ADR-0001 §5). */
    val modeSwitchMs: Int = 250

    // ── 氛围动效 / ambient motion ────────────────────────────────────────────────────────────
    // Kept for the visual character, but capped: the baseline device can't afford 60fps full-screen.

    /** Frame-rate ceiling for ambient animation (DynamicBackground etc.), in fps (ADR-0001 §7). */
    const val ambientFps: Int = 30

    /**
     * One ambient frame at [ambientFps], in nanoseconds. This is the single source of truth for
     * the 30fps cadence — anything that throttles to the ambient budget (e.g.
     * [com.applemusicktv.util.FrameRedrawThrottle]) derives its interval from here instead of
     * repeating the literal.
     */
    const val ambientFrameIntervalNs: Long = 1_000_000_000L / ambientFps
}
