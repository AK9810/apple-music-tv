package com.applemusicktv.util

import com.applemusicktv.ui.theme.MotionBudget
import kotlin.math.ceil

/**
 * Aggregated frame timings for the Dev-menu overlay (see `FrameTimeOverlay`).
 *
 * All values describe the same sliding window: [sampleCount] frames, their P50/P90
 * durations in milliseconds, and how many of them missed the 60 Hz budget.
 */
data class FrameStats(
    val p50Ms: Double,
    val p90Ms: Double,
    val droppedFrames: Int,
    val sampleCount: Int,
) {
    companion object {
        /** No frames in the window yet — the overlay shows placeholders instead of zeros. */
        val EMPTY = FrameStats(p50Ms = 0.0, p90Ms = 0.0, droppedFrames = 0, sampleCount = 0)
    }
}

/**
 * Pure frame-time aggregation, kept out of the UI so it can be unit-tested on the JVM.
 *
 * Percentiles use the nearest-rank definition: sort ascending, then take
 * index = ceil(p * n) - 1. Every reported value is therefore a frame that actually
 * happened, never an interpolated duration the device never drew.
 */
object FrameTimeStats {
    /**
     * One frame at 60 Hz. A frame longer than this is counted as dropped.
     *
     * This is the display's frame budget used to classify samples, not a motion-policy value —
     * animation durations and frame-rate ceilings live in
     * [com.applemusicktv.ui.theme.MotionBudget].
     */
    const val FRAME_BUDGET_NS = 16_600_000L

    private const val NANOS_PER_MS = 1_000_000.0

    /**
     * @param frameDurationsNs total frame durations in nanoseconds (not mutated).
     * @param droppedThresholdNs a frame is dropped when strictly longer than this.
     */
    fun aggregate(
        frameDurationsNs: LongArray,
        droppedThresholdNs: Long = FRAME_BUDGET_NS,
    ): FrameStats {
        if (frameDurationsNs.isEmpty()) return FrameStats.EMPTY

        var dropped = 0
        for (duration in frameDurationsNs) {
            if (duration > droppedThresholdNs) dropped++
        }

        val sorted = frameDurationsNs.sortedArray()
        return FrameStats(
            p50Ms = percentile(sorted, 50.0) / NANOS_PER_MS,
            p90Ms = percentile(sorted, 90.0) / NANOS_PER_MS,
            droppedFrames = dropped,
            sampleCount = sorted.size,
        )
    }

    /** Nearest-rank percentile of an ascending sorted array. */
    private fun percentile(sorted: LongArray, percent: Double): Long {
        val rank = ceil(percent / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }
}

/**
 * The overlay updates its own text at most 30 times a second. 30 fps is visually
 * indistinguishable for a number readout and keeps the overlay from adding a
 * recomposition per frame to the very frames it is measuring.
 */
object FrameRedrawThrottle {
    /** 1/30 s in nanoseconds, derived from the ambient motion budget (one 30fps source). */
    const val MIN_REDRAW_INTERVAL_NS = MotionBudget.ambientFrameIntervalNs

    /**
     * @param lastRedrawNanos timestamp of the last text update; 0 means "never".
     */
    fun shouldRedraw(
        lastRedrawNanos: Long,
        nowNanos: Long,
        minIntervalNs: Long = MIN_REDRAW_INTERVAL_NS,
    ): Boolean {
        if (lastRedrawNanos <= 0L) return true
        // A backwards clock yields a negative delta and therefore no redraw.
        return nowNanos - lastRedrawNanos >= minIntervalNs
    }
}

/**
 * Sliding window of frame durations, trimmed by wall-clock age rather than frame count
 * so a 50 Hz or 120 Hz panel still yields a "last 5 seconds" view.
 *
 * Not thread-safe by design: the frame listener and the overlay ticker both run on the
 * main thread. Samples exactly [maxAgeNanos] old are kept (the window is half-open).
 */
class FrameSampleWindow(private val maxAgeNanos: Long) {
    private val timestamps = ArrayDeque<Long>()
    private val durations = ArrayDeque<Long>()

    fun add(timestampNanos: Long, durationNanos: Long) {
        timestamps.addLast(timestampNanos)
        durations.addLast(durationNanos)
        trim(nowNanos = timestampNanos)
    }

    /** Durations still inside the window, oldest first. */
    fun snapshot(nowNanos: Long): LongArray {
        trim(nowNanos)
        return durations.toLongArray()
    }

    private fun trim(nowNanos: Long) {
        val cutoff = nowNanos - maxAgeNanos
        while (timestamps.isNotEmpty() && timestamps.first() < cutoff) {
            timestamps.removeFirst()
            durations.removeFirst()
        }
    }
}
