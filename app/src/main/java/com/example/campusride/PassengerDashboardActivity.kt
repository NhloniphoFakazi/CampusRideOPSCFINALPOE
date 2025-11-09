package com.example.campusride

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class PassengerDashboardActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var tvWelcome: TextView
    private lateinit var tvInstitution: TextView
    private lateinit var tvBalance: TextView
    private lateinit var tvNoRides: TextView
    private lateinit var btnBookRide: Button
    private lateinit var btnViewAllRides: Button
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var cardMyRides: MaterialCardView
    private lateinit var cardWallet: MaterialCardView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_passenger_dashboard)
        Log.d("DEBUG", "PassengerDashboardActivity created")

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()
        loadPassengerData()
        setupBottomNavigation()
        setupToolbar()

        // Get and display FCM token
        getAndDisplayFCMToken()
    }

    private fun initializeViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvInstitution = findViewById(R.id.tvInstitution)
        tvBalance = findViewById(R.id.tvBalance)
        tvNoRides = findViewById(R.id.tvNoRides)
        btnBookRide = findViewById(R.id.btnBookRide)
        btnViewAllRides = findViewById(R.id.btnViewAllRides)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        cardMyRides = findViewById(R.id.cardMyRides)
        cardWallet = findViewById(R.id.cardWallet)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        Log.d("DEBUG", "Toolbar setup completed")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        Log.d("DEBUG", "onCreateOptionsMenu called")
        try {
            menuInflater.inflate(R.menu.dashboard_menu, menu)
            Log.d("DEBUG", "Menu inflated successfully")

            // Check if settings item exists
            val settingsItem = menu.findItem(R.id.action_settings)
            if (settingsItem != null) {
                Log.d("DEBUG", "Settings item found - title: ${settingsItem.title}")
            } else {
                Log.d("DEBUG", "Settings item NOT found!")
            }
        } catch (e: Exception) {
            Log.e("DEBUG", "Error inflating menu: ${e.message}")
            e.printStackTrace()

            // Fallback: add item programmatically
            menu.add(0, 1001, 0, "Settings")
                .setIcon(android.R.drawable.ic_menu_preferences)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            Log.d("DEBUG", "Added settings item programmatically")
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d("DEBUG", "Menu item clicked: ${item.itemId}")

        return when (item.itemId) {
            R.id.action_settings, 1001 -> {
                Log.d("DEBUG", "Settings clicked - starting SettingsActivity")
                Toast.makeText(this, "Opening Settings...", Toast.LENGTH_SHORT).show()

                try {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    Log.d("DEBUG", "SettingsActivity started successfully")
                } catch (e: Exception) {
                    Log.e("DEBUG", "Error starting SettingsActivity: ${e.message}")
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
                true
            }
            else -> {
                Log.d("DEBUG", "Unknown menu item: ${item.itemId}")
                super.onOptionsItemSelected(item)
            }
        }
    }

    private fun setupClickListeners() {
        btnBookRide.setOnClickListener {
            val intent = Intent(this, BookRideActivity::class.java)
            startActivity(intent)
        }

        btnViewAllRides.setOnClickListener {
            val intent = Intent(this, PassengerRidesActivity::class.java)
            startActivity(intent)
        }

        cardMyRides.setOnClickListener {
            val intent = Intent(this, PassengerProfileActivity::class.java)
            startActivity(intent)
        }

        cardWallet.setOnClickListener {
            val intent = Intent(this, WalletActivity::class.java)
            startActivity(intent)
        }

        // Add long press on welcome text to show FCM token
        tvWelcome.setOnLongClickListener {
            showTokenForDebug()
            true
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_rides -> {
                    val intent = Intent(this, RideHistoryActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_wallet -> {
                    val intent = Intent(this, WalletActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.nav_profile -> {
                    val intent = Intent(this, PassengerProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadPassengerData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("passengers").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: "Passenger"
                    val institution = document.getString("institution") ?: "University"
                    val balance = document.getDouble("walletBalance") ?: 0.0

                    tvWelcome.text = "Welcome, $fullName!"
                    tvInstitution.text = institution
                    tvBalance.text = "Wallet Balance: R${"%.2f".format(balance)}"
                    loadRecentRides(userId)
                }
            }
            .addOnFailureListener {
                tvWelcome.text = "Welcome, Passenger!"
            }
    }

    private fun loadRecentRides(userId: String) {
        tvNoRides.text = "No recent rides"
    }

    // FCM Token Methods
    private fun getAndDisplayFCMToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM_DEBUG", "Passenger FCM Token: $token")

                // Show token in AlertDialog (only on first launch)
                showTokenDialog(token)

                // Save token to Firestore
                saveTokenToFirestore(token)
            } else {
                Log.e("FCM_DEBUG", "Failed to get FCM token: ${task.exception}")
                Toast.makeText(this, "Failed to get FCM token", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showTokenDialog(token: String) {
        val sharedPref = getSharedPreferences("campusride_prefs", Context.MODE_PRIVATE)
        val hasShownToken = sharedPref.getBoolean("has_shown_token", false)

        if (!hasShownToken) {
            AlertDialog.Builder(this)
                .setTitle("FCM Token for Testing")
                .setMessage("Copy this token to Firebase Console:\n\n$token")
                .setPositiveButton("Copy Token") { dialog, which ->
                    // Copy to clipboard
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("FCM Token", token)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "Token copied! Paste in Firebase Console", Toast.LENGTH_LONG).show()

                    // Mark as shown
                    sharedPref.edit().putBoolean("has_shown_token", true).apply()
                }
                .setNegativeButton("Close") { dialog, which ->
                    sharedPref.edit().putBoolean("has_shown_token", true).apply()
                }
                .setCancelable(false)
                .show()
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return

        db.collection("passengers").document(userId)
            .update("fcmToken", token)
            .addOnSuccessListener {
                Log.d("FCM_DEBUG", "FCM token saved to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("FCM_DEBUG", "Failed to save FCM token to Firestore", e)
            }
    }

    private fun showTokenForDebug() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                AlertDialog.Builder(this)
                    .setTitle("Debug: FCM Token")
                    .setMessage(token)
                    .setPositiveButton("Copy") { dialog, which ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("FCM Token", token)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }
}