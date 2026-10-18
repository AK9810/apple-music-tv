package com.applemusicktv.util

import android.content.Context
import androidx.annotation.StringRes

/**
 * A user-facing string the ViewModel never resolves itself.
 *
 * Below API 33 AppCompat applies the chosen app language only to Activity contexts, so
 * resolving through an application context (what ViewModels have) can silently fall back
 * to the system language. The UI layer resolves this with its own Activity context —
 * via `stringResource`/`getString` — and picks up language changes with the recreate.
 */
data class UiString(@StringRes val res: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(res, *args.toTypedArray())
}
