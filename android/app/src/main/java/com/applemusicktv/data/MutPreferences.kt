package com.applemusicktv.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MutPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("apple_music_tv", Context.MODE_PRIVATE)

    // Sign-in happens off-screen (the token is pasted on the phone page) and the UI has to
    // react to it — onboarding's step 2 and the Now Playing "previews only" banner both key
    // off this. A getter-only prefs class would leave them stale until the next screen.
    private val _mut = MutableStateFlow(prefs.getString("mut", "") ?: "")
    val mut: StateFlow<String> = _mut.asStateFlow()

    fun getMUT(): String = _mut.value
    fun setMUT(token: String) {
        _mut.value = token
        prefs.edit { putString("mut", token) }
    }
    fun hasMUT(): Boolean = getMUT().isNotEmpty()
}
