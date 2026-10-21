package com.nhom2.booking.data.settings

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import java.util.Locale

/**
 * In-app language choice (Vietnamese or English), independent of the phone's language.
 * Uses SharedPreferences because it must be read synchronously in attachBaseContext, before any coroutine runs.
 */
object LanguageSettings {
    const val VIETNAMESE = "vi"
    const val ENGLISH = "en"

    private const val PREFS = "settings"
    private const val KEY_LANGUAGE = "language"

    fun current(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
        return saved ?: if (Locale.getDefault().language == ENGLISH) ENGLISH else VIETNAMESE
    }

    fun save(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_LANGUAGE, language) }
    }

    /** Returns a context whose resources use the chosen language; also sets the default locale for API headers. */
    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(current(base))
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
