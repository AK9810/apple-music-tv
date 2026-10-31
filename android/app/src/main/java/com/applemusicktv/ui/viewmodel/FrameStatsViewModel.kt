package com.applemusicktv.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.applemusicktv.data.FrameStatsPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * App-shell scoped holder for the Dev-menu frame-time overlay switch.
 *
 * The overlay is mounted by [com.applemusicktv.ui.AppShell], outside every screen, so it
 * needs its own state holder — the Dev-menu ViewModel is scoped to that route and would be
 * cleared the moment the developer leaves Settings while the overlay is still running.
 */
@HiltViewModel
class FrameStatsViewModel @Inject constructor(
    prefs: FrameStatsPreferences,
) : ViewModel() {
    /** Mirrors [FrameStatsPreferences.enabled] so the overlay mounts/unmounts live. */
    val enabled: StateFlow<Boolean> = prefs.enabled
}
