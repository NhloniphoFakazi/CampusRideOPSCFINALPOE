package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DriverDashboardActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var tvWelcome: TextView
    private lateinit var tvDriverStatus: TextView
    private lateinit var tvDriverInfo: TextView
    private lateinit var tvCompletedRides: TextView
    private lateinit var tvEarnings: TextView
    private lateinit var tvRating: TextView
    private lateinit var btnStartDriving: Button
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var cardViewRides: MaterialCardView
    private lateinit var cardGoOnline: MaterialCardView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_dashboard)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupClickListeners()
        loadDriverData()
        setupBottomNavigation()
        setupToolbar()
    }

    private fun initializeViews() {
        tvWelcome = findViewById(R.id.tvWelcome)
        tvDriverStatus = findViewById(R.id.tvDriverstatus)
        tvDriverInfo = findViewById(R.id.tvDriverInfo)
        tvCompletedRides = findViewById(R.id.tvCompletedRides)
        tvEarnings = findViewById(R.id.tvEarnings)
        tvRating = findViewById(R.id.tvRating)
        btnStartDriving = findViewById(R.id.btnStartDriving)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        cardViewRides = findViewById(R.id.cardViewRides)
        cardGoOnline = findViewById(R.id.cardGoOnline)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.dashboard_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupClickListeners() {
        btnStartDriving.setOnClickListener {
            goOnline()
        }

        // Make "View Rides" card go to Profile
        cardViewRides.setOnClickListener {
            val intent = Intent(this, DriverRideRequestsActivity::class.java)
            startActivity(intent)
        }

        // Make "Go Online" card go to Profile
        cardGoOnline.setOnClickListener {
            val intent = Intent(this, DriverProfileActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    true
                }
                R.id.nav_rides -> {
                    val intent = Intent(this, DriverRideRequestsActivity::class.java)
                    startActivity(intent)
                    true
                }

                R.id.nav_profile -> {
                    val intent = Intent(this, DriverProfileActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }

    private fun loadDriverData() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("drivers").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: "Driver"
                    val vehicleColor = document.getString("vehicleColor") ?: ""
                    val carModel = document.getString("carModel") ?: ""
                    val onlineStatus = document.getBoolean("onlineStatus") ?: false

                    tvWelcome.text = "Welcome, $fullName!"
                    tvDriverStatus.text = "Status: ${if (onlineStatus) "Online" else "Offline"}"
                    tvDriverInfo.text = "$vehicleColor $carModel"

                    btnStartDriving.text = if (onlineStatus) "Go Offline" else "Go Online"

                    btnStartDriving.setOnClickListener {
                        if (onlineStatus) {
                            goOffline()
                        } else {
                            goOnline()
                        }
                    }

                    loadDriverStats(userId)
                }
            }
            .addOnFailureListener {
                tvWelcome.text = "Welcome, Driver!"
                Toast.makeText(this, "Failed to load driver data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadDriverStats(userId: String) {
        // Load completed rides count
        db.collection("rides")
            .whereEqualTo("driverId", userId)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val completedRides = querySnapshot.size()
                tvCompletedRides.text = completedRides.toString()
            }

        // Load earnings
        db.collection("rides")
            .whereEqualTo("driverId", userId)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { querySnapshot ->
                var totalEarnings = 0.0
                for (document in querySnapshot.documents) {
                    val fare = document.getDouble("estimatedFare") ?: 0.0
                    totalEarnings += fare
                }
                tvEarnings.text = "R${"%.2f".format(totalEarnings)}"
            }

        // Load average rating
        db.collection("rideRatings")
            .whereEqualTo("driverId", userId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    tvRating.text = "0.0"
                } else {
                    var totalRating = 0.0
                    for (document in querySnapshot.documents) {
                        val rating = document.getDouble("rating") ?: 0.0
                        totalRating += rating
                    }
                    val averageRating = totalRating / querySnapshot.size()
                    tvRating.text = "%.1f".format(averageRating)
                }
            }
    }

    private fun goOnline() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("drivers").document(userId)
            .update("onlineStatus", true)
            .addOnSuccessListener {
                tvDriverStatus.text = "Status: Online"
                tvDriverInfo.text = "You're online and ready for rides"
                btnStartDriving.text = "Go Offline"

                btnStartDriving.setOnClickListener {
                    goOffline()
                }

                Toast.makeText(this, "You're now online", Toast.LENGTH_SHORT).show()

                val intent = Intent(this, DriverRideRequestsActivity::class.java)
                startActivity(intent)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to go online", Toast.LENGTH_SHORT).show()
            }
    }

    private fun goOffline() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("drivers").document(userId)
            .update("onlineStatus", false)
            .addOnSuccessListener {
                tvDriverStatus.text = "Status: Offline"
                tvDriverInfo.text = "You're offline"
                btnStartDriving.text = "Go Online"

                btnStartDriving.setOnClickListener {
                    goOnline()
                }

                Toast.makeText(this, "You're now offline", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to go offline", Toast.LENGTH_SHORT).show()
            }
    }
}