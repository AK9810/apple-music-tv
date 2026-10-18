package com.applemusicktv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.applemusicktv.BuildConfig
import com.applemusicktv.R
import com.applemusicktv.ui.viewmodel.DevMenuViewModel
import com.applemusicktv.ui.viewmodel.NowPlayingBackground
import com.applemusicktv.ui.viewmodel.PlayerViewModel
import com.applemusicktv.util.AppLanguage
import com.applemusicktv.util.UpdateChecker
import com.applemusicktv.util.UpdateInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DevMenuScreen(
    playerVm: PlayerViewModel,
    onDataRefresh: () -> Unit = {},
    initialUpdate: UpdateInfo? = null,
    modifier: Modifier = Modifier,
) {
    val vm: DevMenuViewModel = hiltViewModel()
    val state by vm.state.collectAsState()
    val pstate by playerVm.state.collectAsState()
    var pcIpDraft by remember(state.pcServerIp) { mutableStateOf(state.pcServerIp) }
    var showDev by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    // PC IP is a press-to-edit button, not a live field: a focused BasicTextField pops the
    // Fire TV IME by itself the moment D-pad focus lands on it.
    var pcIpEditing by remember { mutableStateOf(false) }
    val pcIpFocus = remember { FocusRequester() }
    val ctx = LocalContext.current
    val rootView = androidx.compose.ui.platform.LocalView.current
    fun hideIme() = runCatching {
        ctx.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(rootView.windowToken, 0)
    }

    // Live network activity — polled from the shared NetworkLog buffer.
    var netLogs by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(showDev) {
        while (showDev) { netLogs = vm.networkLogs(); kotlinx.coroutines.delay(1000) }
    }

    Box(modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 60.dp, vertical = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.settings_title), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)

            // ── LANGUAGE ──────────────────────────────────────────────────
            // Per-app locale. Applying it re-creates this activity; ViewModels and playback survive.
            SectionLabel(stringResource(R.string.settings_section_language))
            Stepper(
                label = stringResource(R.string.settings_language_label),
                value = stringResource(AppLanguage.current().labelRes),
                sub = stringResource(R.string.settings_language_sub),
                onDec = { AppLanguage.apply(AppLanguage.step(AppLanguage.current(), -1)) },
                onInc = { AppLanguage.apply(AppLanguage.step(AppLanguage.current(), 1)) },
            )

            // ── SOFTWARE ──────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_software))
            UpdatesSection(initialUpdate)
            PhoneServerRow(state.webServerUrl, playerVm)

            // ── PLAYBACK ──────────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_playback))
            Stepper(
                label = stringResource(R.string.settings_crossfade),
                value = stringResource(R.string.settings_crossfade_value, state.crossfadeMs / 1000f),
                sub = stringResource(R.string.settings_crossfade_sub),
                onDec = { vm.setCrossfade(state.crossfadeMs - 500) },
                onInc = { vm.setCrossfade(state.crossfadeMs + 500) },
            )
            Toggle(
                label = stringResource(R.string.settings_standalone), on = state.standaloneOn,
                sub = stringResource(if (state.standaloneOn) R.string.settings_standalone_on else R.string.settings_standalone_off),
                onToggle = { vm.toggleStandalone() },
            )
            Toggle(
                label = stringResource(R.string.settings_volume_leveling), on = pstate.volumeLeveling,
                sub = stringResource(if (pstate.volumeLeveling) R.string.settings_volume_leveling_on else R.string.settings_volume_leveling_off),
                onToggle = { playerVm.toggleVolumeLeveling() },
            )
            Toggle(
                label = stringResource(R.string.settings_background_play), on = pstate.backgroundPlayEnabled,
                sub = stringResource(if (pstate.backgroundPlayEnabled) R.string.settings_background_play_on else R.string.settings_background_play_off),
                onToggle = { playerVm.toggleBackgroundPlay() },
            )

            // ── NOW PLAYING ───────────────────────────────────────────────
            SectionLabel(stringResource(R.string.settings_section_now_playing))
            Stepper(
                label = stringResource(R.string.settings_background),
                value = stringResource(pstate.nowPlayingBackground.labelRes),
                sub = stringResource(R.string.settings_background_sub),
                onDec = { playerVm.stepNowPlayingBackground(-1) },
                onInc = { playerVm.stepNowPlayingBackground(1) },
            )
            Toggle(
                label = stringResource(R.string.settings_show_info), on = pstate.showNowPlayingInfo,
                sub = stringResource(if (pstate.showNowPlayingInfo) R.string.settings_show_info_on else R.string.settings_show_info_off),
                onToggle = { playerVm.toggleNowPlayingInfo() },
            )
            // Intensity is the orb SIZE/reactivity; hidden on Black (no orbs). Remembered per mode.
            if (pstate.nowPlayingBackground != NowPlayingBackground.BLACK) Stepper(
                label = stringResource(R.string.settings_intensity),
                value = intensityLabel(pstate.beatIntensity),
                sub = stringResource(R.string.settings_intensity_sub),
                onDec = { playerVm.stepBeatIntensity(-1) },
                onInc = { playerVm.stepBeatIntensity(1) },
            )
            // Orb drift speed — only meaningful in Projector.
            if (pstate.nowPlayingBackground == NowPlayingBackground.PROJECTOR) Stepper(
                label = stringResource(R.string.settings_orb_speed),
                value = orbSpeedLabel(pstate.orbSpeed),
                sub = stringResource(R.string.settings_orb_speed_sub),
                onDec = { playerVm.stepOrbSpeed(-1) },
                onInc = { playerVm.stepOrbSpeed(1) },
            )
            Stepper(
                label = stringResource(R.string.settings_lyrics_size),
                value = lyricsScaleLabel(pstate.lyricsScale),
                sub = stringResource(R.string.settings_lyrics_size_sub),
                onDec = { playerVm.stepLyricsScale(-1) },
                onInc = { playerVm.stepLyricsScale(1) },
            )
            Toggle(
                label = stringResource(R.string.settings_rounded_artwork), on = pstate.artworkRounded,
                sub = stringResource(if (pstate.artworkRounded) R.string.settings_rounded_artwork_on else R.string.settings_rounded_artwork_off),
                onToggle = { playerVm.toggleArtworkRounded() },
            )
            Toggle(
                label = stringResource(R.string.settings_motion_artwork), on = pstate.motionArtworkEnabled,
                sub = stringResource(if (pstate.motionArtworkEnabled) R.string.settings_motion_artwork_on else R.string.settings_motion_artwork_off),
                onToggle = { playerVm.toggleMotionArtwork() },
            )
            Toggle(
                label = stringResource(R.string.settings_reduce_motion), on = pstate.reduceMotion,
                sub = stringResource(if (pstate.reduceMotion) R.string.settings_reduce_motion_on else R.string.settings_reduce_motion_off),
                onToggle = { playerVm.toggleReduceMotion() },
            )
            Toggle(
                label = stringResource(R.string.settings_low_power), on = pstate.lowPowerMode,
                sub = stringResource(if (pstate.lowPowerMode) R.string.settings_low_power_on else R.string.settings_low_power_off),
                onToggle = { playerVm.toggleLowPowerMode() },
            )
            Stepper(
                label = stringResource(R.string.settings_screensaver),
                value = screensaverLabel(pstate.screensaverTimeoutMin),
                sub = stringResource(R.string.settings_screensaver_sub),
                onDec = { playerVm.stepScreensaverTimeout(-1) },
                onInc = { playerVm.stepScreensaverTimeout(1) },
            )
            Toggle(
                label = stringResource(R.string.settings_ambient_screensaver), on = pstate.screensaverKeepBackground,
                sub = stringResource(if (pstate.screensaverKeepBackground) R.string.settings_ambient_screensaver_on else R.string.settings_ambient_screensaver_off),
                onToggle = { playerVm.toggleScreensaverKeepBackground() },
            )

            SectionLabel(stringResource(R.string.settings_section_remote))
            var remote by remember { mutableStateOf(playerVm.remoteOverride()) }
            val remoteOrder = listOf(
                com.applemusicktv.data.OnboardingPreferences.REMOTE_AUTO,
                com.applemusicktv.data.OnboardingPreferences.REMOTE_FIRE,
                com.applemusicktv.data.OnboardingPreferences.REMOTE_GOOGLE,
            )
            fun remoteLabelRes(v: String) = when (v) {
                com.applemusicktv.data.OnboardingPreferences.REMOTE_FIRE -> R.string.settings_remote_fire
                com.applemusicktv.data.OnboardingPreferences.REMOTE_GOOGLE -> R.string.settings_remote_google
                else -> R.string.settings_remote_auto
            }
            fun stepRemote(dir: Int) {
                val i = (remoteOrder.indexOf(remote).coerceAtLeast(0) + dir + remoteOrder.size) % remoteOrder.size
                remote = remoteOrder[i]; playerVm.setRemoteOverride(remote)
            }
            Stepper(
                label = stringResource(R.string.settings_remote_type),
                value = stringResource(remoteLabelRes(remote)),
                sub = stringResource(R.string.settings_remote_sub),
                onDec = { stepRemote(-1) },
                onInc = { stepRemote(1) },
            )

            // ── A/V SYNC ──────────────────────────────────────────────────
            // One delay drives both the beat visuals and the lyric clock. A ~200 ms display baseline is
            // built in (so this normally reads 0); Automatic adds a Bluetooth estimate on top when a BT
            // output is live. "Extra" is your own nudge on top of all that.
            SectionLabel(stringResource(R.string.settings_section_av_sync))
            Text(
                stringResource(R.string.settings_av_output, pstate.avOutput.resolve(ctx)),
                fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium,
            )
            Text(
                if (pstate.avSyncAuto && pstate.avOnBluetooth) stringResource(R.string.settings_av_bt_delay)
                else stringResource(R.string.settings_av_standard),
                fontSize = 10.sp, color = Color(0xFF999999), modifier = Modifier.padding(bottom = 4.dp),
            )
            Toggle(
                label = stringResource(R.string.settings_av_auto),
                on = pstate.avSyncAuto,
                sub = stringResource(if (pstate.avSyncAuto) R.string.settings_av_auto_on else R.string.settings_av_auto_off),
                onToggle = { playerVm.setAvSyncAuto(!pstate.avSyncAuto) },
            )
            Stepper(
                label = stringResource(R.string.settings_av_extra_offset),
                value = stringResource(R.string.settings_av_offset_value, state.lyricsOffsetMs.toInt()),
                sub = stringResource(R.string.settings_av_extra_sub),
                onDec = { vm.setLyricsOffset(state.lyricsOffsetMs - 50) },
                onInc = { vm.setLyricsOffset(state.lyricsOffsetMs + 50) },
            )

            Spacer(Modifier.height(6.dp))
            // ── Reveal dev menu ───────────────────────────────────────────
            ActionBtn(
                if (showDev) stringResource(R.string.settings_hide_dev_menu) else stringResource(R.string.settings_open_dev_menu),
                Color(0xFF1C1C1E),
            ) { showDev = !showDev }

            if (showDev) {
                SectionLabel(stringResource(R.string.settings_section_account))
                StatusChip(
                    stringResource(R.string.settings_bearer_token), state.hasBearer,
                    if (!state.hasBearer) stringResource(R.string.settings_bearer_refresh_hint) else stringResource(R.string.settings_status_active),
                )
                StatusChip(
                    "Music-User-Token", state.hasMUT,
                    state.mutSetAt?.let { stringResource(R.string.settings_mut_set_at, it) }
                        ?: if (!state.hasMUT) stringResource(R.string.settings_mut_set_via_phone)
                        else stringResource(R.string.settings_status_active),
                )
                StatusChip(
                    stringResource(R.string.settings_server_label), state.serverOk,
                    if (state.serverOk) stringResource(R.string.settings_server_reachable) else stringResource(R.string.settings_server_unreachable),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionBtn(stringResource(R.string.settings_recheck_server), Color(0xFF1A2A1A), small = true) { vm.recheckServer(playerVm, onDone = onDataRefresh) }
                    ActionBtn(stringResource(R.string.common_refresh), Color(0xFF2A2A2A), small = true) { vm.refresh(onDone = onDataRefresh) }
                    ActionBtn(stringResource(R.string.settings_replay_setup), Color(0xFF2A2A1A), small = true) { playerVm.resetOnboarding() }
                    ActionBtn(stringResource(R.string.settings_clear_token), Color(0xFF3A1A1A), small = true) { vm.clearMUT() }
                    // Hard quit: stop playback + kill the process so the next launch is a true cold start.
                    ActionBtn(stringResource(R.string.settings_force_quit), Color(0xFF3A1A1A), small = true) {
                        playerVm.stopPlayback()
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                }

                SectionLabel(stringResource(R.string.settings_section_connection))
                if (state.webServerUrl.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().background(Color(0xFF0D1F0D), RoundedCornerShape(10.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(stringResource(R.string.settings_phone_web_server), fontSize = 9.sp, color = Color(0xFF6BCB77), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Text(state.webServerUrl, fontSize = 15.sp, color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.settings_phone_server_hint), fontSize = 11.sp, color = Color(0xFF557755))
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.settings_pc_server), fontSize = 9.sp, color = Color(0xFF888888), fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pcIpEditing) {
                            // Editing: OK opened this, so the field may hold focus + IME. Done/Back closes.
                            LaunchedEffect(Unit) { runCatching { pcIpFocus.requestFocus() } }
                            androidx.activity.compose.BackHandler(enabled = true) { pcIpEditing = false; hideIme() }
                            Box(
                                Modifier.weight(1f).background(Color(0xFF0D0D0D), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                BasicTextField(
                                    value = pcIpDraft, onValueChange = { pcIpDraft = it }, singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                                    cursorBrush = SolidColor(Color(0xFFFA233B)),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { vm.setPcServerIp(pcIpDraft); pcIpEditing = false; hideIme() }),
                                    modifier = Modifier.fillMaxWidth().focusRequester(pcIpFocus),
                                    decorationBox = { inner ->
                                        if (pcIpDraft.isEmpty()) Text(stringResource(R.string.settings_pc_ip_placeholder), fontSize = 13.sp, color = Color(0xFF444444), fontFamily = FontFamily.Monospace)
                                        inner()
                                    },
                                )
                            }
                            ActionBtn(stringResource(R.string.common_done), Color(0xFF2A2A2A), small = true) { vm.setPcServerIp(pcIpDraft); pcIpEditing = false; hideIme() }
                        } else {
                            // Idle: a plain button. Focus can land here with no IME. OK opens the editor.
                            Surface(
                                onClick = { pcIpEditing = true },
                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF0D0D0D), focusedContainerColor = Color(0xFF1E1E1E)),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    pcIpDraft.ifEmpty { stringResource(R.string.settings_pc_ip_press_edit) },
                                    fontSize = 13.sp, color = if (pcIpDraft.isEmpty()) Color(0xFF666666) else Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                )
                            }
                        }
                        if (pcIpDraft.isNotEmpty()) ActionBtn(stringResource(R.string.common_clear), Color(0xFF3A1A1A), small = true) { pcIpDraft = ""; vm.setPcServerIp("") }
                    }
                    if (state.pcServerIp.isNotEmpty())
                        Text(stringResource(R.string.settings_pc_active, state.pcServerIp), fontSize = 10.sp, color = Color(0xFF6BCB77), fontFamily = FontFamily.Monospace)
                }

                // ── LOGS (app + network) ──────────────────────────────────
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_logs))
                    ActionBtn(stringResource(R.string.common_clear), Color(0xFF2A2A2A), small = true) { vm.clearLogs() }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                        .background(Color(0xFF0D0D0D), RoundedCornerShape(8.dp)).padding(12.dp),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(netLogs.reversed()) { line ->
                        Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF6C8CD5), lineHeight = 13.sp)
                    }
                    items(state.logs.reversed()) { log ->
                        Text(log.message, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                            color = when (log.level) {
                                "ERROR" -> Color(0xFFFF6B6B); "WARN" -> Color(0xFFFFD93D)
                                "OK"    -> Color(0xFF6BCB77); else  -> Color(0xFFAAAAAA)
                            }, lineHeight = 13.sp)
                    }
                }

                SectionLabel(stringResource(R.string.settings_section_bug_report))
                BugReportRow(state.webServerUrl)

                SectionLabel(stringResource(R.string.settings_section_danger_zone))
                ActionBtn(stringResource(R.string.settings_reset_app), Color(0xFF4A1414)) { confirmReset = true }
                Text(stringResource(R.string.settings_reset_sub), fontSize = 10.sp, color = Color(0xFF775555))
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (confirmReset) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(360.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(14.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.settings_reset_dialog_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(stringResource(R.string.settings_reset_dialog_body), fontSize = 12.sp, color = Color(0xFF999999))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionBtn(stringResource(R.string.common_cancel), Color(0xFF2A2A2A)) { confirmReset = false }
                    ActionBtn(stringResource(R.string.settings_reset_confirm), Color(0xFFB22222)) {
                        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        am.clearApplicationUserData()
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneServerRow(webServerUrl: String, playerVm: PlayerViewModel) {
    var enabled by remember { mutableStateOf(playerVm.webServerEnabled()) }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_phone_server_title), fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    if (enabled) stringResource(R.string.settings_phone_server_on) else stringResource(R.string.common_off),
                    fontSize = 10.sp, color = Color(0xFF777777),
                )
            }
            // Toggle to turn the :8080 page on/off (it's on by default).
            Surface(
                onClick = { enabled = !enabled; playerVm.setWebServerEnabled(enabled) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (enabled) Color(0xFF34C759) else Color(0xFF3A3A3C),
                    focusedContainerColor = if (enabled) Color(0xFF4AD866) else Color(0xFF5A5A5C),
                ),
            ) {
                Text(
                    if (enabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        if (enabled && webServerUrl.isNotEmpty()) {
            Text(stringResource(R.string.settings_phone_server_desc), fontSize = 10.sp, color = Color(0xFF777777))
            Text(webServerUrl, fontSize = 15.sp, color = Color(0xFF6BCB77),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        } else if (enabled) {
            Text(stringResource(R.string.settings_phone_server_wifi), fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@Composable
private fun BugReportRow(webServerUrl: String) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(stringResource(R.string.settings_bug_report_title), fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
        if (webServerUrl.isNotEmpty()) {
            Text(stringResource(R.string.settings_bug_report_open), fontSize = 10.sp, color = Color(0xFF777777))
            Text("$webServerUrl/report", fontSize = 13.sp, color = Color(0xFF6BCB77),
                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.settings_bug_report_desc),
                fontSize = 10.sp, color = Color(0xFF777777))
        } else {
            Text(stringResource(R.string.settings_bug_report_wifi), fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun UpdatesSection(initialUpdate: UpdateInfo? = null) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    // A string-resource id, not a formatted string: the language can change under this screen.
    var status by remember { mutableStateOf<Int?>(null) }
    var update by remember { mutableStateOf(initialUpdate) }
    var progress by remember { mutableStateOf(-1f) } // -1 = not downloading
    var beta by remember { mutableStateOf(com.applemusicktv.util.UpdatePreferences.betaEnabled(ctx)) }

    // Fire OS often RECREATES MainActivity when the unknown-sources settings screen returns, which
    // wipes all Compose state (the old in-memory parked file was lost, forcing a re-check + re-download).
    // Survive that: the downloaded APK already lives at cacheDir/updates/update.apk, and a persisted
    // flag records that an install is waiting on the grant. On resume we re-fire from disk, so
    // recreation costs nothing.
    fun prefs() = ctx.getSharedPreferences("update_state", android.content.Context.MODE_PRIVATE)
    fun cachedApk() = java.io.File(java.io.File(ctx.cacheDir, "updates"), "update.apk")
    fun hasGrant() = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
        ctx.packageManager.canRequestPackageInstalls()

    fun installOrRequestGrant(apk: java.io.File) {
        if (hasGrant()) {
            prefs().edit().putBoolean("pending_install", false).apply()
            UpdateChecker.install(ctx, apk); return
        }
        // Park across a possible recreation: remember we owe an install, keep the APK on disk.
        prefs().edit().putBoolean("pending_install", true).apply()
        status = R.string.settings_update_allow_installs
        val pkgUri = android.net.Uri.parse("package:${ctx.packageName}")
        // The per-app screen isn't always present on Fire OS; fall back to the generic intent rather
        // than throwing ActivityNotFoundException at someone who was only trying to update.
        val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri)
        runCatching { ctx.startActivity(intent) }.onFailure {
            runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)) }
        }
    }

    // On resume (including after a full recreation), if an install is owed, the grant is now held, and
    // the downloaded APK is still cached → install it. No re-check, no re-download.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME &&
                prefs().getBoolean("pending_install", false) && hasGrant()) {
                val apk = cachedApk()
                if (apk.exists() && apk.length() > 0) {
                    prefs().edit().putBoolean("pending_install", false).apply()
                    status = null
                    UpdateChecker.install(ctx, apk)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    fun runCheck() {
        if (checking) return
        checking = true; status = R.string.settings_update_checking_github; update = null
        scope.launch {
            val res = UpdateChecker.check(beta)
            checking = false
            res.onSuccess { info ->
                if (info == null) status = R.string.settings_update_latest
                else { update = info; status = null }
            }.onFailure { status = R.string.settings_update_check_failed }
        }
    }

    Column(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_app_version), fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(
                    status?.let { stringResource(it) }
                        ?: stringResource(R.string.settings_version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.GIT_SHA),
                    fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (update == null && progress < 0f) {
                ActionBtn(
                    if (checking) stringResource(R.string.settings_update_checking) else stringResource(R.string.settings_check_updates),
                    Color(0xFF1C1C2E), small = true,
                ) { runCheck() }
            }
        }

        // Opt into prerelease builds. Re-checks immediately so the result reflects the channel.
        Toggle(
            label = stringResource(R.string.settings_beta_updates), on = beta,
            sub = stringResource(if (beta) R.string.settings_beta_on else R.string.settings_beta_off),
            onToggle = {
                beta = !beta
                com.applemusicktv.util.UpdatePreferences.setBeta(ctx, beta)
                runCheck()
            },
        )

        // An update is available → show version, notes and a download button.
        update?.let { info ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.settings_update_available, info.version), fontSize = 13.sp, color = Color(0xFF6BCB77), fontWeight = FontWeight.SemiBold)
                if (info.notes.isNotEmpty())
                    Text(info.notes.take(400), fontSize = 10.sp, color = Color(0xFF999999), lineHeight = 14.sp)
                if (progress < 0f) {
                    val mb = if (info.sizeBytes > 0) stringResource(R.string.settings_update_size, info.sizeBytes / 1024f / 1024f) else ""
                    ActionBtn(stringResource(R.string.settings_download_install, mb), Color(0xFF14351F)) {
                        progress = 0f
                        scope.launch {
                            UpdateChecker.download(ctx, info) { progress = it }
                                .onSuccess { apk -> progress = -1f; installOrRequestGrant(apk) }
                                .onFailure { progress = -1f; status = R.string.settings_update_download_failed; update = null }
                        }
                    }
                } else {
                    Text(stringResource(R.string.settings_update_downloading, (progress * 100).toInt()), fontSize = 12.sp, color = Color.White)
                    Box(Modifier.fillMaxWidth().height(6.dp).background(Color(0xFF2A2A2C), RoundedCornerShape(50))) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp)
                            .background(Color(0xFFFA233B), RoundedCornerShape(50)))
                    }
                }
            }
        }
    }
}

/** Calm / Normal / Strong / Crazy for the beat-reaction multiplier. */
@Composable
internal fun intensityLabel(f: Float): String = stringResource(
    when {
        f < 0.8f -> R.string.settings_intensity_calm
        f < 1.5f -> R.string.settings_intensity_normal
        f < 2.5f -> R.string.settings_intensity_strong
        else     -> R.string.settings_intensity_crazy
    }
)

@Composable
internal fun orbSpeedLabel(f: Float): String = stringResource(
    when {
        f < 0.85f -> R.string.settings_orb_slow
        f < 1.3f  -> R.string.settings_intensity_normal
        else      -> R.string.settings_orb_fast
    }
)

@Composable
internal fun lyricsScaleLabel(f: Float): String = stringResource(
    when {
        f < 1.1f -> R.string.settings_lyrics_small
        f < 1.4f -> R.string.settings_intensity_normal
        f < 1.8f -> R.string.settings_lyrics_large
        else     -> R.string.settings_lyrics_huge
    }
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Stepper(label: String, value: String, sub: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF161618), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBtn("−", onDec)
            Text(value, fontSize = 15.sp, color = Color(0xFFFA233B), fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 70.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            StepBtn("+", onInc)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StepBtn(sym: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2A2C), focusedContainerColor = Color(0xFFFA233B)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        modifier = Modifier.size(38.dp),
    ) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(sym, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Toggle(label: String, on: Boolean, sub: String, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF161618), focusedContainerColor = Color(0xFF232325)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Medium)
                Text(sub, fontSize = 10.sp, color = Color(0xFF777777), modifier = Modifier.padding(top = 2.dp))
            }
            Box(
                Modifier.width(56.dp).height(30.dp)
                    .background(if (on) Color(0xFFFA233B) else Color(0xFF3A3A3C), RoundedCornerShape(50)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.padding(3.dp).size(24.dp).background(Color.White, RoundedCornerShape(50)))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), fontSize = 10.sp, color = Color(0xFF666666),
        fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatusChip(label: String, ok: Boolean, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(8.dp).background(if (ok) Color(0xFF6BCB77) else Color(0xFFFF6B6B), RoundedCornerShape(50)))
        Column {
            Text(label, fontSize = 13.sp, color = Color(0xFFCCCCCC))
            if (sub != null) Text(sub, fontSize = 10.sp, color = Color(0xFF777777))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionBtn(label: String, color: Color, small: Boolean = false, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = color, focusedContainerColor = color.copy(alpha = 0.7f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.height(if (small) 32.dp else 40.dp)) {
        Box(Modifier.padding(horizontal = if (small) 12.dp else 18.dp), Alignment.Center) {
            Text(label, fontSize = if (small) 11.sp else 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}
