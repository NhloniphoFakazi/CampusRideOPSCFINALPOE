package com.example.campusride

import android.app.Application
import android.content.Context
import org.osmdroid.config.Configuration as OsmConfig


/**
 * Application class. Ensures the saved language is applied globally on app start.
 * Make sure AndroidManifest.xml has: android:name=".CampusRideApp"
 */
class CampusRideApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // load osmdroid (if used in your project)
        OsmConfig.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        // Ensure resources are updated to the saved locale
        LanguageHelper.applyLanguageGlobally(this)
    }

    /**
     * Ensure base context is wrapped for locale before anything else.
     * This helps activities and services to see the correct locale early.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(base))
    }

    /**
     * Convenience method you can call from Activities if you prefer:
     * e.g. (application as CampusRideApp).setLocale("zu")
     */
    fun setLocale(language: String) {
        LanguageHelper.persistLanguage(this, language)
        // After persisting, update resources for the Application context globally
        LanguageHelper.applyLanguageGlobally(this)
    }
}