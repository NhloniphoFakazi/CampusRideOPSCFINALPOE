package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class MainActivity : BaseActivity() {

    private val splashTimeOut: Long = 3000 // 3 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hide action bar
        supportActionBar?.hide()

        Handler(Looper.getMainLooper()).postDelayed({
            // Navigate to WelcomeActivity
            val intent = Intent(this, WelcomeActivity::class.java)
            startActivity(intent)
            finish()
        }, splashTimeOut)
    }
}