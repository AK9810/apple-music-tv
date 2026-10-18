package com.applemusicktv.util

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.applemusicktv.R

/**
 * App UI language, backed by AppCompat's per-app locales.
 *
 * [SYSTEM] is an empty locale list — strings then follow the device locale (values-zh
 * kicks in automatically on a Chinese TV). Changing the language re-creates the
 * activity: ViewModels and playback survive, plain `remember` UI state does not.
 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("", R.string.lang_system),
    ENGLISH("en", R.string.lang_english),
    CHINESE("zh-CN", R.string.lang_chinese);

    companion object {
        /** The language currently applied — what the Settings stepper shows. */
        fun current(): AppLanguage {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return SYSTEM
            val first = locales.toLanguageTags().substringBefore('-')
            return AppLanguage.entries.firstOrNull {
                it.tag.isNotEmpty() && it.tag.substringBefore('-').equals(first, ignoreCase = true)
            } ?: SYSTEM
        }

        /** Applies [language] app-wide; AppCompat re-creates our activity for us. */
        fun apply(language: AppLanguage) {
            AppCompatDelegate.setApplicationLocales(
                if (language.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag),
            )
        }

        /** Neighbour in the SYSTEM → ENGLISH → CHINESE cycle, for the ± stepper. */
        fun step(current: AppLanguage, direction: Int): AppLanguage {
            val values = AppLanguage.entries
            val i = (values.indexOf(current).coerceAtLeast(0) + direction + values.size) % values.size
            return values[i]
        }

        /**
         * A context whose resources use the chosen app language even below API 33, where
         * AppCompat applies the locale only to Activity contexts. Needed for strings the
         * ViewModels resolve themselves (notifications); UI strings go through [UiString]
         * and the Activity context instead.
         */
        fun localizedContext(base: Context): Context {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return base
            val config = Configuration(base.resources.configuration)
            // Configuration.setLocales is API 24+; minSdk is 23.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.setLocales(android.os.LocaleList.forLanguageTags(locales.toLanguageTags()))
            } else {
                @Suppress("DEPRECATION")
                config.locale = locales.get(0) ?: java.util.Locale.getDefault()
            }
            return base.createConfigurationContext(config)
        }
    }
}
