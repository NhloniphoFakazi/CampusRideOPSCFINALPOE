package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.widget.Button

class WelcomeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnLogin.setOnClickListener {
            // Navigate directly to LoginActivity
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnRegister.setOnClickListener {
            // Navigate to UserTypeSelectionActivity for registration
            val intent = Intent(this, UserTypeSelectionActivity::class.java)
            startActivity(intent)
        }
    }
}