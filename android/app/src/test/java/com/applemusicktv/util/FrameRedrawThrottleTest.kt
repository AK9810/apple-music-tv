package com.applemusicktv.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The frame-time overlay re-renders its text at most 30 times a second. The decision is a
 * pure function so it can be pinned here instead of being inferred from a 16 ms ticker.
 */
class FrameRedrawThrottleTest {

    @Test
    fun `first draw is always allowed`() {
        // 0 means "never drawn" — the overlay must show numbers on its first tick.
        assertTrue(FrameRedrawThrottle.shouldRedraw(lastRedrawNanos = 0, nowNanos = 1_000_000_000))
    }

    @Test
    fun `a 16 ms gap is too soon`() {
        val last = 1_000_000_000L
        assertFalse(FrameRedrawThrottle.shouldRedraw(last, nowNanos = last + 16_000_000))
    }

    @Test
    fun `a full 30fps interval is allowed`() {
        val last = 1_000_000_000L
        assertTrue(
            FrameRedrawThrottle.shouldRedraw(
                last, nowNanos = last + FrameRedrawThrottle.MIN_REDRAW_INTERVAL_NS,
            ),
        )
    }

    @Test
    fun `one full 30fps interval is allowed but one nanosecond less is not`() {
        val last = 5_000_000_000L
        assertTrue(FrameRedrawThrottle.shouldRedraw(last, nowNanos = last + 33_333_333))
        assertFalse(FrameRedrawThrottle.shouldRedraw(last, nowNanos = last + 33_333_333 - 1))
    }

    @Test
    fun `a backwards clock does not trigger a redraw`() {
        // System.nanoTime is monotonic, but a bad caller must not storm the overlay.
        assertFalse(FrameRedrawThrottle.shouldRedraw(lastRedrawNanos = 10, nowNanos = 5))
    }
}
