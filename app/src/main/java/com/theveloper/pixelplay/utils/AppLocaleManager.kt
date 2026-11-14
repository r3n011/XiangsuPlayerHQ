package com.theveloper.pixelplay.utils

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.data.preferences.AppLanguage
import java.util.Locale

object AppLocaleManager {
    private const val PREFERENCES_NAME = "app_locale_preferences"
    private const val KEY_LANGUAGE_TAG = "app_language_tag"

    /**
     * Resolve text for event callbacks and background work in the selected app language.
     * Compose UI should use localizedStringResource with its current configuration instead.
     */
    fun getString(@StringRes resourceId: Int, vararg formatArgs: Any): String {
        val application = PixelPlayApplication.appContext()
        val context = contextForLocale(application, currentLocale())
        return if (formatArgs.isEmpty()) context.getString(resourceId)
        else context.getString(resourceId, *formatArgs)
    }

    /** Keep secondary system languages from overriding the Chinese fallback for these labels. */
    internal fun contextForLocale(
        base: Context,
        locale: Locale,
        configuration: Configuration = base.resources.configuration
    ): Context {
        @Suppress("DEPRECATION")
        val hasOnlyRequestedLocale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales.size() == 1 && configuration.locales[0] == locale
        } else {
            configuration.locale == locale
        }
        if (hasOnlyRequestedLocale) return base

        return base.createConfigurationContext(Configuration(configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        })
    }

    fun currentLocale(): Locale {
        val languageTag = currentLanguageTag(PixelPlayApplication.appContext())
        @Suppress("DEPRECATION")
        return if (languageTag.isBlank()) Resources.getSystem().configuration.locale
        else Locale.forLanguageTag(languageTag)
    }

    fun currentLanguageTag(context: Context): String =
        AppLanguage.normalize(
            context
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_TAG, AppLanguage.SYSTEM.tag)
                .orEmpty()
        )

    fun applyLanguage(context: Context, languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        context
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, normalized)
            .apply()

        val locales = if (normalized.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalized)
        }

        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locales.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun wrapContext(base: Context): Context {
        val languageTag = currentLanguageTag(base)
        if (languageTag.isBlank()) return base

        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)

        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
        }

        return base.createConfigurationContext(configuration)
    }
}
