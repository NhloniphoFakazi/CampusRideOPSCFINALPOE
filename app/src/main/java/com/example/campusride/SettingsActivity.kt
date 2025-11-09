package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class SettingsActivity : BaseActivity() {

    private lateinit var radioGroupLanguage: RadioGroup
    private lateinit var radioEnglish: RadioButton
    private lateinit var radioZulu: RadioButton
    private lateinit var btnSaveLanguage: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        radioGroupLanguage = findViewById(R.id.radioGroupLanguage)
        radioEnglish = findViewById(R.id.radioEnglish)
        radioZulu = findViewById(R.id.radioZulu)
        btnSaveLanguage = findViewById(R.id.btnSaveLanguage)

        val currentLang = LanguageHelper.getSavedLanguage(this)
        when (currentLang) {
            "zu" -> radioZulu.isChecked = true
            else -> radioEnglish.isChecked = true
        }

        btnSaveLanguage.setOnClickListener {
            val selectedId = radioGroupLanguage.checkedRadioButtonId
            if (selectedId == -1) {
                Toast.makeText(this, getString(R.string.select_language_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCode = when (selectedId) {
                R.id.radioZulu -> "zu"
                else -> "en"
            }

            // Save and apply globally
            LanguageHelper.setLocale(this, selectedCode)

            // Force update the application context
            LanguageHelper.applyLanguageGlobally(this)

            // Restart app to ensure all activities pick up new locale
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Finish current activity
            finish()

            Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show()
        }
    }
}