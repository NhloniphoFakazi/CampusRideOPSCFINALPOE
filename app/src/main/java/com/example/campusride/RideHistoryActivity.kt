package com.example.campusride

import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.*

class RideHistoryActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RideHistoryAdapter
    private lateinit var tvNoRides: TextView
    private lateinit var progressBar: ProgressBar

    private val rideHistory = mutableListOf<RideHistory>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_history)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupRecyclerView()
        loadRideHistory()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewRideHistory)
        tvNoRides = findViewById(R.id.tvNoRides)
        progressBar = findViewById(R.id.progressBar)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }
    }

    private fun setupRecyclerView() {
        adapter = RideHistoryAdapter(rideHistory)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadRideHistory() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = ProgressBar.VISIBLE

        Log.d("RideHistory", "Loading ride history for user: $userId")

        db.collection("rides")
            .whereEqualTo("passengerId", userId)
            .whereIn("status", listOf("driver_assigned", "driver_arrived", "picked_up", "completed", "cancelled", "rejected"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                progressBar.visibility = ProgressBar.GONE

                rideHistory.clear()
                Log.d("RideHistory", "Found ${querySnapshot.documents.size} rides in query")

                for (document in querySnapshot.documents) {
                    val status = document.getString("status") ?: "unknown"
                    val pickupLocation = document.getString("pickupLocation") ?: "Unknown pickup"
                    val dropoffLocation = document.getString("dropoffLocation") ?: "Unknown dropoff"
                    val fare = document.getDouble("estimatedFare") ?: 0.0
                    val date = document.getTimestamp("createdAt")?.toDate() ?: Date()
                    val driverName = document.getString("driverName") ?: "No driver assigned"

                    val ride = RideHistory(
                        rideId = document.id,
                        pickupLocation = pickupLocation,
                        dropoffLocation = dropoffLocation,
                        fare = fare,
                        date = date,
                        status = status,
                        driverName = driverName
                    )
                    rideHistory.add(ride)
                    Log.d("RideHistory", "Added ride: $pickupLocation -> $dropoffLocation, Status: $status, Driver: $driverName")
                }

                adapter.notifyDataSetChanged()
                updateEmptyState()

                Log.d("RideHistory", "Successfully loaded ${rideHistory.size} rides for user: $userId")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = ProgressBar.GONE
                val errorMsg = "Failed to load ride history: ${e.message}"
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                Log.e("RideHistory", errorMsg, e)
                updateEmptyState()
            }
    }

    private fun updateEmptyState() {
        if (rideHistory.isEmpty()) {
            tvNoRides.visibility = TextView.VISIBLE
            recyclerView.visibility = RecyclerView.GONE
            Log.d("RideHistory", "No rides found - showing empty state")
        } else {
            tvNoRides.visibility = TextView.GONE
            recyclerView.visibility = RecyclerView.VISIBLE
        }
    }
}

class RideHistoryAdapter(private val rideHistory: List<RideHistory>) :
    RecyclerView.Adapter<RideHistoryAdapter.ViewHolder>() {

    class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val tvRoute: TextView = itemView.findViewById(R.id.tvRoute)
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvFare: TextView = itemView.findViewById(R.id.tvFare)
        val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        val tvDriver: TextView = itemView.findViewById(R.id.tvDriver)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ride_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ride = rideHistory[position]
        holder.tvRoute.text = "${ride.pickupLocation} → ${ride.dropoffLocation}"
        holder.tvDate.text = android.text.format.DateFormat.format("MMM dd, yyyy - hh:mm a", ride.date)
        holder.tvFare.text = "R${"%.2f".format(ride.fare)}"
        holder.tvDriver.text = "Driver: ${ride.driverName}"

        val statusText = when (ride.status) {
            "driver_assigned", "driver_arrived", "picked_up" -> "In Progress"
            "completed" -> "Completed"
            "cancelled" -> "Cancelled"
            "rejected" -> "Rejected"
            else -> ride.status.replace("_", " ").capitalize()
        }

        holder.tvStatus.text = statusText

        when (ride.status) {
            "completed" -> holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
            "cancelled", "rejected" -> holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
            else -> holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"))
        }
    }

    override fun getItemCount(): Int {
        return rideHistory.size
    }
}

data class RideHistory(
    val rideId: String,
    val pickupLocation: String,
    val dropoffLocation: String,
    val fare: Double,
    val date: Date,
    val status: String,
    val driverName: String
)