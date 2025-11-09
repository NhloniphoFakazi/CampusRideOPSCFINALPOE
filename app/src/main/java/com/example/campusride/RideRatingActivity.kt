package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RatingBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RideRatingActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var ratingBar: RatingBar
    private lateinit var etFeedback: TextInputEditText
    private lateinit var btnSubmit: Button

    private var rideId: String = ""
    private var driverId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_rating)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        rideId = intent.getStringExtra("rideId") ?: ""
        driverId = intent.getStringExtra("driverId") ?: ""

        if (rideId.isEmpty() || driverId.isEmpty()) {
            Toast.makeText(this, "Invalid ride data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
    }

    private fun initializeViews() {
        ratingBar = findViewById(R.id.ratingBar)
        etFeedback = findViewById(R.id.etFeedback)
        btnSubmit = findViewById(R.id.btnSubmit)
    }

    private fun setupClickListeners() {
        btnSubmit.setOnClickListener {
            submitRating()
        }
    }

    private fun submitRating() {
        val rating = ratingBar.rating
        val feedback = etFeedback.text.toString().trim()
        val passengerId = auth.currentUser?.uid ?: return

        if (rating == 0f) {
            Toast.makeText(this, "Please provide a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val ratingData = hashMapOf(
            "rideId" to rideId,
            "driverId" to driverId,
            "passengerId" to passengerId,
            "rating" to rating.toDouble(),
            "feedback" to feedback,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        db.collection("rideRatings").document(rideId)
            .set(ratingData)
            .addOnSuccessListener {
                db.collection("rides").document(rideId)
                    .update("status", "rated")
                    .addOnSuccessListener {
                        Toast.makeText(this, "Thank you for your rating!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, PassengerDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit rating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}