package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class UserTypeSelectionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_type_selection)

        val btnDriver = findViewById<Button>(R.id.btnDriver)
        val btnPassenger = findViewById<Button>(R.id.btnPassenger)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)

        btnDriver.setOnClickListener {
            val intent = Intent(this, DriverRegistrationActivity::class.java)
            startActivity(intent)
        }

        btnPassenger.setOnClickListener {
            val intent = Intent(this, PassengerRegistrationActivity::class.java)
            startActivity(intent)
        }

        tvLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }
    }
}