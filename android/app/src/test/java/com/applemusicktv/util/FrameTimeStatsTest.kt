package com.applemusicktv.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the pure aggregation behind the Dev-menu frame-time overlay.
 *
 * Percentiles use the nearest-rank definition: sort ascending, then
 * index = ceil(p * n) - 1. That is the convention the ticket asks for
 * ("P50 / P90 frame duration"), and it always returns a frame that was
 * actually measured — no interpolated value that never occurred on screen.
 */
class FrameTimeStatsTest {

    private fun ms(v: Long) = v * 1_000_000L

    @Test
    fun `normal 60fps window reports p50 p90 and no dropped frames`() {
        // Five frames, one per budget-ish duration, deliberately unsorted.
        val stats = FrameTimeStats.aggregate(
            longArrayOf(ms(16), ms(8), ms(12), ms(10), ms(14)),
        )

        assertEquals(5, stats.sampleCount)
        assertEquals(12.0, stats.p50Ms, 0.001)   // nearest rank ceil(0.50 * 5) - 1 = 2
        assertEquals(16.0, stats.p90Ms, 0.001)   // nearest rank ceil(0.90 * 5) - 1 = 4
        assertEquals(0, stats.droppedFrames)
    }

    @Test
    fun `frames over 16 point 6 ms count as dropped`() {
        val stats = FrameTimeStats.aggregate(
            longArrayOf(ms(10), ms(17), ms(20), ms(33)),
        )

        assertEquals(4, stats.sampleCount)
        assertEquals(17.0, stats.p50Ms, 0.001)
        assertEquals(33.0, stats.p90Ms, 0.001)
        assertEquals(3, stats.droppedFrames)     // 17, 20 and 33 ms — 10 ms is fine
    }

    @Test
    fun `a frame exactly on the budget is not dropped`() {
        val onBudget = FrameTimeStats.aggregate(longArrayOf(FrameTimeStats.FRAME_BUDGET_NS))
        val overBudget = FrameTimeStats.aggregate(longArrayOf(FrameTimeStats.FRAME_BUDGET_NS + 1))

        assertEquals(0, onBudget.droppedFrames)  // the rule is "over" 16.6 ms, not "at"
        assertEquals(1, overBudget.droppedFrames)
    }

    @Test
    fun `extreme long frames are kept in the percentiles and counted once`() {
        // A 2.5 s stall dwarfs the normal frames; P90 must land on it, not clamp it.
        val stats = FrameTimeStats.aggregate(
            longArrayOf(ms(16), ms(2_500), ms(16), ms(120_000)),
        )

        assertEquals(4, stats.sampleCount)
        assertEquals(16.0, stats.p50Ms, 0.001)   // rank 2 of [16, 16, 2500, 120000]
        assertEquals(120_000.0, stats.p90Ms, 0.001)
        assertEquals(2, stats.droppedFrames)
    }

    @Test
    fun `empty window reports zeros and no samples`() {
        val stats = FrameTimeStats.aggregate(longArrayOf())

        assertEquals(0, stats.sampleCount)
        assertEquals(0.0, stats.p50Ms, 0.0)
        assertEquals(0.0, stats.p90Ms, 0.0)
        assertEquals(0, stats.droppedFrames)
    }
}

/** The overlay keeps the last ~5 s of frames and must age samples out as time advances. */
class FrameSampleWindowTest {

    private fun ms(v: Long) = v * 1_000_000L

    @Test
    fun `snapshot drops samples older than the window`() {
        val window = FrameSampleWindow(maxAgeNanos = ms(5_000))
        window.add(timestampNanos = ms(0), durationNanos = 11)
        window.add(timestampNanos = ms(1_000), durationNanos = 22)
        window.add(timestampNanos = ms(4_000), durationNanos = 33)
        window.add(timestampNanos = ms(6_000), durationNanos = 44)

        // At 6 s: the 0 s sample is 6 s old and gone; the 1 s sample is exactly at the edge.
        assertArrayEquals(longArrayOf(22, 33, 44), window.snapshot(nowNanos = ms(6_000)))
    }

    @Test
    fun `snapshot of a fresh window is empty`() {
        val window = FrameSampleWindow(maxAgeNanos = ms(5_000))

        assertArrayEquals(longArrayOf(), window.snapshot(nowNanos = ms(42)))
    }
}
