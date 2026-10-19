package com.applemusicktv

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.applemusicktv.ui.AppShell
import com.applemusicktv.ui.viewmodel.MusicVideoViewModel
import com.applemusicktv.ui.viewmodel.NavigationViewModel
import com.applemusicktv.ui.viewmodel.PlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

// AppCompatActivity (not ComponentActivity) is load-bearing for per-app language: below
// API 33 AppCompatDelegate only re-creates AppCompat-hosted activities when the locale
// changes, so the UI flips language instantly on Fire TV instead of after a restart.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val navVm: NavigationViewModel by viewModels()
    private val playerVm: PlayerViewModel by viewModels()
    private val mvVm: MusicVideoViewModel by viewModels()

    /** Guards [exitApp] — onStop follows the finish(), and re-entering would double-save/kill. */
    private var exiting = false

    /** Set by [onUserLeaveHint]: the next stop is the user leaving (Home / another app). */
    private var userLeaving = false

    /**
     * Another app's screen we opened ourselves (APK installer, unknown-sources grant) is up.
     * That stop is not the user leaving us for good, so [onStop] only pauses.
     */
    private var leavingForExternalApp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppShell() }
    }

    /**
     * Quit for real: save the session, silence audio + video, remove the task and end the
     * process. On a 1.5–2 GB Fire TV a cached background process still pins the Widevine CDM,
     * the decoders, the Coil caches and the phone web server's sockets — leaving the app should
     * hand all of that back. Every launch is therefore a cold start; PlayerViewModel.restoreState()
     * brings the last track back (paused) from the saved state.
     */
    fun exitApp() {
        if (exiting) return
        exiting = true
        // Save FIRST: stop() resets the player position, and the process dies milliseconds later.
        runCatching { playerVm.stopForExit() }
        // A music video runs on its own player, so stopping the audio one leaves its output going.
        runCatching { playerVm.clearVideoRequest(); mvVm.close() }
        runCatching { finishAndRemoveTask() }
        // finishAndRemoveTask() is an async binder call to the activity manager; give it (and the
        // committed prefs) a moment to land before the process disappears.
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, EXIT_KILL_DELAY_MS)
    }

    /**
     * Run [action] knowing it opens another app's UI (APK installer, the installer-permission
     * screen). The stop that follows is not the user leaving us for good, so [onStop] only
     * pauses. Returns true if the launch happened; a failure drops the exemption again so the
     * next real exit still quits.
     */
    fun launchExternally(action: () -> Unit): Boolean {
        leavingForExternalApp = true
        return runCatching(action).isSuccess.also { if (!it) leavingForExternalApp = false }
    }

    override fun onResume() {
        super.onResume()
        userLeaving = false
        leavingForExternalApp = false
    }

    /**
     * Home / recents / "open another app" — the user is leaving, which on this app means quit.
     * Being *covered* (the TV's ambient screensaver, a system overlay, another activity we
     * started) does NOT call this, which is exactly right: those must not kill the process, or
     * the :8080 page could not be used to set the token while the TV idles.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeaving = true
    }

    /**
     * Leaving quits the app — no background playback, no media service, no system PiP, and no
     * cached process holding the decoders. Exceptions:
     *  - a configuration change (the per-app language switch) re-creates the activity and must
     *    keep the ViewModels — and the playing audio — alive;
     *  - an external screen we launched ourselves only pauses;
     *  - standby (display off) counts as leaving, so a sleeping TV doesn't keep the process.
     */
    override fun onStop() {
        super.onStop()
        if (exiting || isChangingConfigurations) return
        if (leavingForExternalApp) { playerVm.pause(); return }
        val screenOff = runCatching {
            !(getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        }.getOrDefault(false)
        if (userLeaving || screenOff) exitApp() else playerVm.pause()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // While a video is active, media keys drive the VIDEO — never the (paused) audio
        // player. D-pad keys still fall through so the fullscreen video screen (or the UI
        // behind it) can handle them.
        if (navVm.isOnMusicVideo) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { mvVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> { mvVm.prev(); return true }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE ->
                    { mvVm.togglePlayPause(); return true }
                // A video can be the current track while you browse other tabs (it keeps playing).
                // Only toggle the in-video queue when actually ON the video screen; elsewhere Menu
                // brings the video fullscreen (Now Playing), same as for audio.
                KeyEvent.KEYCODE_MENU -> {
                    if (navVm.isOnNowPlaying) mvVm.toggleQueue() else navVm.navigateToNowPlaying()
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU -> {
                    if (navVm.isOnNowPlaying) navVm.toggleQueuePanel()
                    else navVm.navigateToNowPlaying()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { playerVm.togglePlayPause(); return true }
                KeyEvent.KEYCODE_MEDIA_NEXT     -> { playerVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { playerVm.prev(); return true }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { playerVm.next(); return true }
                KeyEvent.KEYCODE_MEDIA_REWIND       -> { playerVm.prev(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private companion object {
        /** Long enough for finishAndRemoveTask() and the committed prefs to reach the system. */
        const val EXIT_KILL_DELAY_MS = 250L
    }
}
