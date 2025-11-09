package com.example.campusride

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

object LanguageHelper {
    private const val PREFS_NAME = "AppSettings"
    private const val KEY_LANGUAGE = "language"
    private const val DEFAULT_LANG = "en"

    fun getSavedLanguage(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    fun persistLanguage(context: Context, languageCode: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    /**
     * Wrap the context so resources are returned in the selected locale.
     * Call from Application.attachBaseContext and Activity.attachBaseContext.
     */
    fun wrapContext(base: Context): Context {
        val lang = getSavedLanguage(base)
        return updateResources(base, lang)
    }

    /**
     * Call to change language and persist it immediately.
     * Returns a wrapped Context (useful if you need an updated one right away).
     */
    fun setLocale(context: Context, languageCode: String): Context {
        persistLanguage(context, languageCode)
        return updateResources(context, languageCode)
    }

    private fun updateResources(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val res: Resources = context.resources
        val config = Configuration(res.configuration)

        // For API >= 24 use createConfigurationContext
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            // Set layout direction based on locale
            config.setLayoutDirection(locale)
            val contextWithConfig = context.createConfigurationContext(config)
            // Also update the base context's resources
            res.updateConfiguration(config, res.displayMetrics)
            contextWithConfig
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            // Set layout direction for older APIs
            config.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)
            context
        }
    }

    /**
     * Force refresh the application context to apply language changes globally
     */
    fun applyLanguageGlobally(context: Context) {
        val lang = getSavedLanguage(context)
        updateResources(context.applicationContext, lang)
    }
}