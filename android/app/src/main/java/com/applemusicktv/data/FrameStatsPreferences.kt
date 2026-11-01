package com.applemusicktv.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dev-menu frame-time overlay switch.
 *
 * Default OFF on purpose: the overlay attaches a `Window.addOnFrameMetricsAvailableListener`
 * and runs a 30 fps text ticker, so it must cost nothing unless a developer asks for the
 * numbers. The StateFlow is what makes the Dev-menu toggle mount/unmount the overlay
 * immediately, with no restart (same pattern as [CrossfadePreferences]).
 */
@Singleton
class FrameStatsPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("frame_stats_prefs", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean("overlay_enabled", false))
    /** Emits on every toggle so the overlay and the Dev-menu switch stay in sync live. */
    val enabled: StateFlow<Boolean> = _enabled

    fun isEnabled(): Boolean = _enabled.value

    fun setEnabled(on: Boolean) {
        prefs.edit { putBoolean("overlay_enabled", on) }
        _enabled.value = on
    }
}
