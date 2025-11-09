package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges

class DriverRideTrackingActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tvStatus: TextView
    private lateinit var tvPassengerName: TextView
    private lateinit var tvPickupLocation: TextView
    private lateinit var tvDropoffLocation: TextView
    private lateinit var tvEstimatedFare: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var tvDistance: TextView
    private lateinit var btnArrived: Button
    private lateinit var btnStartRide: Button
    private lateinit var btnCompleteRide: Button
    private lateinit var progressBar: ProgressBar

    private var rideId: String = ""
    private var rideListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_ride_tracking)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        rideId = intent.getStringExtra("rideId") ?: ""
        if (rideId.isEmpty()) {
            Toast.makeText(this, "Invalid ride ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        setupRideListener()
    }

    private fun initializeViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvPassengerName = findViewById(R.id.tvPassengerName)
        tvPickupLocation = findViewById(R.id.tvPickupLocation)
        tvDropoffLocation = findViewById(R.id.tvDropoffLocation)
        tvEstimatedFare = findViewById(R.id.tvEstimatedFare)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        tvDistance = findViewById(R.id.tvDistance)
        btnArrived = findViewById(R.id.btnArrived)
        btnStartRide = findViewById(R.id.btnStartRide)
        btnCompleteRide = findViewById(R.id.btnCompleteRide)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupClickListeners() {
        btnArrived.setOnClickListener {
            updateRideStatus("driver_arrived")
        }

        btnStartRide.setOnClickListener {
            updateRideStatus("picked_up")
        }

        btnCompleteRide.setOnClickListener {
            updateRideStatus("completed")
        }
    }

    private fun setupRideListener() {
        rideListener = db.collection("rides").document(rideId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Error listening to ride updates", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    updateRideUI(snapshot)
                }
            }
    }

    private fun updateRideUI(snapshot: com.google.firebase.firestore.DocumentSnapshot) {
        val status = snapshot.getString("status") ?: "driver_assigned"
        val passengerName = snapshot.getString("passengerName") ?: "Passenger"
        val pickupLocation = snapshot.getString("pickupLocation") ?: ""
        val dropoffLocation = snapshot.getString("dropoffLocation") ?: ""
        val estimatedFare = snapshot.getDouble("estimatedFare") ?: 0.0
        val estimatedTime = snapshot.getLong("estimatedTime") ?: 0
        val distance = snapshot.getDouble("distance") ?: 0.0

        tvStatus.text = "Status: ${getStatusText(status)}"
        tvPassengerName.text = passengerName
        tvPickupLocation.text = pickupLocation
        tvDropoffLocation.text = dropoffLocation
        tvEstimatedFare.text = "R${"%.2f".format(estimatedFare)}"
        tvEstimatedTime.text = "$estimatedTime min"
        tvDistance.text = "Distance: ${"%.1f".format(distance)} km"

        when (status) {
            "driver_assigned" -> {
                btnArrived.isEnabled = true
                btnStartRide.isEnabled = false
                btnCompleteRide.isEnabled = false
            }
            "driver_arrived" -> {
                btnArrived.isEnabled = false
                btnStartRide.isEnabled = true
                btnCompleteRide.isEnabled = false
            }
            "picked_up" -> {
                btnArrived.isEnabled = false
                btnStartRide.isEnabled = false
                btnCompleteRide.isEnabled = true
            }
            "completed" -> {
                btnArrived.isEnabled = false
                btnStartRide.isEnabled = false
                btnCompleteRide.isEnabled = false
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this, DriverDashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                }, 3000)
            }
        }
    }

    private fun getStatusText(status: String): String {
        return when (status) {
            "driver_assigned" -> "Assigned - Head to pickup location"
            "driver_arrived" -> "Arrived at pickup location"
            "picked_up" -> "Passenger picked up - On the way"
            "in_progress" -> "On the way to destination"
            "completed" -> "Completed"
            else -> status
        }
    }

    private fun updateRideStatus(newStatus: String) {
        val updates = hashMapOf<String, Any>(
            "status" to newStatus,
            "updatedAt" to Timestamp.now()
        )

        db.collection("rides").document(rideId)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Status updated to: $newStatus", Toast.LENGTH_SHORT).show()

                if (newStatus == "completed") {
                    db.collection("rides").document(rideId).get()
                        .addOnSuccessListener { rideDoc ->
                            val passengerId = rideDoc.getString("passengerId") ?: ""
                            val pickupLocation = rideDoc.getString("pickupLocation") ?: ""
                            val dropoffLocation = rideDoc.getString("dropoffLocation") ?: ""
                            val fare = rideDoc.getDouble("estimatedFare") ?: 0.0
                            val distance = rideDoc.getDouble("distance") ?: 0.0

                            if (passengerId.isNotEmpty()) {
                                LocalSyncHelper.saveRideToHistory(
                                    this,
                                    passengerId = passengerId,
                                    driverId = auth.currentUser?.uid,
                                    status = "completed",
                                    origin = pickupLocation,
                                    destination = dropoffLocation,
                                    fare = fare,
                                    distance = distance,
                                    optionalRideId = rideId
                                )
                                Log.d("RideTracking", "Saved completed ride to history for passenger: $passengerId")
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update status: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        rideListener?.remove()
    }
}