package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class DriverRideRequestsActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RideRequestAdapter
    private lateinit var tvNoRides: TextView
    private lateinit var progressBar: ProgressBar

    private var rideListener: ListenerRegistration? = null
    private val rideRequests = mutableListOf<RideRequest>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_ride_requests)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupRecyclerView()
        loadRideRequests()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewRideRequests)
        tvNoRides = findViewById(R.id.tvNoRides)
        progressBar = findViewById(R.id.progressBar)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)?.apply {
            setNavigationOnClickListener { finish() }
        }
    }

    private fun setupRecyclerView() {
        adapter = RideRequestAdapter(rideRequests) { rideRequest ->
            showRideDetailsDialog(rideRequest)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadRideRequests() {
        progressBar.visibility = ProgressBar.VISIBLE

        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("drivers").document(userId).get()
            .addOnSuccessListener { driverDoc ->
                val isOnline = driverDoc.getBoolean("onlineStatus") ?: false
                if (!isOnline) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Please go online to see ride requests", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                rideListener = db.collection("rides")
                    .whereEqualTo("status", "searching")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshots, error ->
                        progressBar.visibility = ProgressBar.GONE

                        if (error != null) {
                            Toast.makeText(this, "Error loading ride requests: ${error.message}", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }

                        rideRequests.clear()
                        snapshots?.documents?.forEach { document ->
                            val rideRequest = RideRequest(
                                rideId = document.id,
                                passengerName = document.getString("passengerName") ?: "Passenger",
                                pickupLocation = document.getString("pickupLocation") ?: "",
                                dropoffLocation = document.getString("dropoffLocation") ?: "",
                                estimatedFare = document.getDouble("estimatedFare") ?: 0.0,
                                estimatedTime = document.getLong("estimatedTime")?.toInt() ?: 0,
                                distance = document.getDouble("distance") ?: 0.0,
                                vehicleType = document.getString("vehicleType") ?: "Standard",
                                createdAt = document.getTimestamp("createdAt"),
                                passengerId = document.getString("passengerId") ?: ""
                            )
                            rideRequests.add(rideRequest)
                        }

                        adapter.notifyDataSetChanged()
                        updateEmptyState()
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Failed to verify driver status", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun updateEmptyState() {
        if (rideRequests.isEmpty()) {
            tvNoRides.visibility = TextView.VISIBLE
            recyclerView.visibility = RecyclerView.GONE
        } else {
            tvNoRides.visibility = TextView.GONE
            recyclerView.visibility = RecyclerView.VISIBLE
        }
    }

    private fun showRideDetailsDialog(rideRequest: RideRequest) {
        val message = """
            Passenger: ${rideRequest.passengerName}
            Pickup: ${rideRequest.pickupLocation}
            Dropoff: ${rideRequest.dropoffLocation}
            Fare: R${"%.2f".format(rideRequest.estimatedFare)}
            Time: ${rideRequest.estimatedTime} minutes
            Distance: ${"%.1f".format(rideRequest.distance)} km
            Vehicle Type: ${rideRequest.vehicleType}
        """.trimIndent()

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Ride Request Details")
            .setMessage(message)
            .setPositiveButton("Accept Ride") { _, _ ->
                acceptRide(rideRequest)
            }
            .setNeutralButton("Reject") { _, _ ->
                rejectRide(rideRequest)
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun acceptRide(rideRequest: RideRequest) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = ProgressBar.VISIBLE

        db.collection("drivers").document(currentUser.uid)
            .get()
            .addOnSuccessListener { driverDoc ->
                if (!driverDoc.exists()) {
                    progressBar.visibility = ProgressBar.GONE
                    Toast.makeText(this, "Driver profile not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val driverName = driverDoc.getString("fullName") ?: "Driver"
                val driverPhone = driverDoc.getString("phoneNumber") ?: ""
                val vehicleModel = driverDoc.getString("carModel") ?: ""
                val vehicleColor = driverDoc.getString("vehicleColor") ?: ""
                val vehicleRegistration = driverDoc.getString("carRegistration") ?: ""

                val updates = hashMapOf<String, Any>(
                    "status" to "driver_assigned",
                    "driverId" to currentUser.uid,
                    "driverName" to driverName,
                    "driverPhone" to driverPhone,
                    "vehicleModel" to vehicleModel,
                    "vehicleColor" to vehicleColor,
                    "vehicleRegistration" to vehicleRegistration,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )

                db.collection("rides").document(rideRequest.rideId)
                    .update(updates)
                    .addOnSuccessListener {
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this, "Ride accepted successfully!", Toast.LENGTH_SHORT).show()

                        if (rideRequest.passengerId.isNotEmpty()) {
                            try {
                                LocalSyncHelper.saveRideToHistory(
                                    this,
                                    passengerId = rideRequest.passengerId,
                                    driverId = currentUser.uid,
                                    status = "driver_assigned",
                                    origin = rideRequest.pickupLocation,
                                    destination = rideRequest.dropoffLocation,
                                    fare = rideRequest.estimatedFare,
                                    distance = rideRequest.distance,
                                    optionalRideId = rideRequest.rideId
                                )
                            } catch (e: Exception) {
                                Log.e("LocalSave", "Local save failed but ride is in Firebase: ${e.message}")
                            }
                        }

                        val intent = Intent(this, DriverRideTrackingActivity::class.java)
                        intent.putExtra("rideId", rideRequest.rideId)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this, "Failed to accept ride: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("RideAccept", "Ride update failed: ${e.message}")
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Error loading driver profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun rejectRide(rideRequest: RideRequest) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = ProgressBar.VISIBLE

        val updates = hashMapOf<String, Any>(
            "status" to "rejected",
            "rejectedBy" to currentUser.uid,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("rides").document(rideRequest.rideId)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Ride rejected", Toast.LENGTH_SHORT).show()

                if (rideRequest.passengerId.isNotEmpty()) {
                    try {
                        LocalSyncHelper.saveRideToHistory(
                            this,
                            passengerId = rideRequest.passengerId,
                            driverId = currentUser.uid,
                            status = "rejected",
                            origin = rideRequest.pickupLocation,
                            destination = rideRequest.dropoffLocation,
                            fare = rideRequest.estimatedFare,
                            distance = rideRequest.distance,
                            optionalRideId = rideRequest.rideId
                        )
                    } catch (e: Exception) {
                        Log.e("LocalSave", "Local save failed: ${e.message}")
                    }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Failed to reject ride: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        rideListener?.remove()
    }
}

class RideRequestAdapter(
    private val rideRequests: List<RideRequest>,
    private val onItemClick: (RideRequest) -> Unit
) : RecyclerView.Adapter<RideRequestAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val passengerName: TextView = itemView.findViewById(R.id.tvPassengerName)
        val pickupLocation: TextView = itemView.findViewById(R.id.tvPickupLocation)
        val dropoffLocation: TextView = itemView.findViewById(R.id.tvDropoffLocation)
        val fare: TextView = itemView.findViewById(R.id.tvEstimatedFare)
        val distance: TextView = itemView.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rideRequest = rideRequests[position]
        holder.passengerName.text = rideRequest.passengerName
        holder.pickupLocation.text = rideRequest.pickupLocation
        holder.dropoffLocation.text = rideRequest.dropoffLocation
        holder.fare.text = "R${"%.2f".format(rideRequest.estimatedFare)}"
        holder.distance.text = "${"%.1f".format(rideRequest.distance)} km"

        holder.itemView.setOnClickListener {
            onItemClick(rideRequest)
        }
    }

    override fun getItemCount(): Int {
        return rideRequests.size
    }
}

data class RideRequest(
    val rideId: String,
    val passengerName: String,
    val pickupLocation: String,
    val dropoffLocation: String,
    val estimatedFare: Double,
    val estimatedTime: Int,
    val distance: Double,
    val vehicleType: String,
    val createdAt: com.google.firebase.Timestamp?,
    val passengerId: String
)