package com.applemusicktv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.applemusicktv.R
import com.applemusicktv.util.FrameRedrawThrottle
import com.applemusicktv.util.FrameSampleWindow
import com.applemusicktv.util.FrameStats
import com.applemusicktv.util.FrameTimeStats
import kotlinx.coroutines.delay
import java.util.Locale

/** "Last ~5 s" window the overlay reports on. */
private const val WINDOW_NANOS = 5_000_000_000L

/** How often the ticker wakes to ask the throttle whether an update is due. */
private const val TICK_MS = 16L

/**
 * Dev-only frame-time readout: P50 / P90 frame duration over the last 5 s plus a dropped-frame
 * count (a frame over 16.6 ms misses the 60 Hz budget).
 *
 * Mounted by [com.applemusicktv.ui.AppShell] only while the Dev-menu switch is on, so when the
 * switch is off there is no listener, no ticker and no composition. Plain text on a translucent
 * background — no blur, no shadow, no glow (the TV perf budget forbids them).
 *
 * Uses `Window.addOnFrameMetricsAvailableListener`, which is API 24+; the app's minSdk is 23,
 * so on older devices this draws nothing at all.
 */
@Composable
fun FrameTimeOverlay(modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
    FrameTimeOverlayApi24(modifier)
}

@RequiresApi(Build.VERSION_CODES.N)
@Composable
private fun FrameTimeOverlayApi24(modifier: Modifier) {
    val window = LocalContext.current.findActivity()?.window ?: return
    val samples = remember { FrameSampleWindow(maxAgeNanos = WINDOW_NANOS) }
    var stats by remember { mutableStateOf(FrameStats.EMPTY) }

    // The listener lives exactly as long as the overlay is composed: switching the Dev-menu
    // toggle off disposes this effect and detaches it, so measured frames cost nothing again.
    DisposableEffect(window) {
        val handler = Handler(Looper.getMainLooper())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            samples.add(
                timestampNanos = System.nanoTime(),
                durationNanos = metrics.getMetric(FrameMetrics.TOTAL_DURATION),
            )
        }
        window.addOnFrameMetricsAvailableListener(listener, handler)
        onDispose { window.removeOnFrameMetricsAvailableListener(listener) }
    }

    // Text updates are capped at 30 fps: a readout does not need 60 Hz, and recomposing at the
    // same rate as the frames being measured would pollute the measurement.
    LaunchedEffect(Unit) {
        var lastRedrawNanos = 0L
        while (true) {
            delay(TICK_MS)
            val now = System.nanoTime()
            if (!FrameRedrawThrottle.shouldRedraw(lastRedrawNanos, now)) continue
            lastRedrawNanos = now
            stats = FrameTimeStats.aggregate(samples.snapshot(now))
        }
    }

    val text = if (stats.sampleCount == 0) {
        stringResource(R.string.frame_stats_waiting)
    } else {
        // One positional-format resource so the whole readout translates as a unit (the `ms`
        // inside formatMs is a unit, not prose, so it stays out of the resource).
        stringResource(
            R.string.frame_stats_readout,
            formatMs(stats.p50Ms),
            formatMs(stats.p90Ms),
            stats.droppedFrames,
        )
    }

    Box(
        modifier = modifier
            .background(Color(0xB3000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun formatMs(ms: Double): String = String.format(Locale.US, "%.1fms", ms)

/** LocalContext can be wrapped (themes, previews); walk up to the owning [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
