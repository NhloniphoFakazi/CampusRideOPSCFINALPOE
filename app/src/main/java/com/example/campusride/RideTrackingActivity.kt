package com.example.campusride

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RideTrackingActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPickupLocation: TextView
    private lateinit var tvDropoffLocation: TextView
    private lateinit var tvEstimatedFare: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var tvDistance: TextView

    private val db = FirebaseFirestore.getInstance()
    private var rideListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "RideTrackingActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_tracking)

        // Initialize only the views that exist in your layout
        tvStatus = findViewById(R.id.tvStatus)
        tvPickupLocation = findViewById(R.id.tvPickupLocation)
        tvDropoffLocation = findViewById(R.id.tvDropoffLocation)
        tvEstimatedFare = findViewById(R.id.tvEstimatedFare)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        tvDistance = findViewById(R.id.tvDistance)

        val rideId = intent.getStringExtra("rideId")
        if (rideId.isNullOrEmpty()) {
            tvStatus.text = "No ride selected."
            return
        }

        // Show immediate message
        tvStatus.text = "Searching for drivers..."

        // Start listening to ride doc updates
        setupRideListener(rideId)
    }

    private fun setupRideListener(rideId: String) {
        rideListener = db.collection("rides").document(rideId).addSnapshotListener { snapshot, exception ->
            if (exception != null) {
                Log.w(TAG, "Listener error", exception)
                tvStatus.text = "Connection error"
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                tvStatus.text = "Ride not found"
                return@addSnapshotListener
            }

            // Update ride status
            val status = snapshot.getString("status") ?: "unknown"
            tvStatus.text = "Status: ${status.replaceFirstChar { it.uppercase() }}"

            // Update trip information from booking
            updateTripInfo(snapshot)
        }
    }

    private fun updateTripInfo(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        // Get the trip information that was input from booking page
        val pickupLocation = snapshot.getString("pickupLocation") ?: "Not set"
        val dropoffLocation = snapshot.getString("dropoffLocation") ?: "Not set"
        val estimatedFare = snapshot.getDouble("estimatedFare") ?: 0.0
        val estimatedTime = snapshot.getLong("estimatedTime") ?: 0
        val distance = snapshot.getDouble("distance") ?: 0.0
        val vehicleType = snapshot.getString("vehicleType") ?: "Standard"

        // Update UI with trip information
        tvPickupLocation.text = pickupLocation
        tvDropoffLocation.text = dropoffLocation
        tvEstimatedFare.text = "R${"%.2f".format(estimatedFare)}"
        tvEstimatedTime.text = if (estimatedTime > 0) "$estimatedTime min" else "--"
        tvDistance.text = "%.1f km".format(distance)

        Log.d(TAG, "Trip info - From: $pickupLocation, To: $dropoffLocation")
        Log.d(TAG, "Fare: R$estimatedFare, Time: $estimatedTime min, Distance: ${"%.1f".format(distance)} km")
    }

    override fun onDestroy() {
        super.onDestroy()
        rideListener?.remove()
    }
}