package com.example.campusride

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.campusride.MainActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class DriverProfileActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // UI Components
    private lateinit var etFullName: TextInputEditText
    private lateinit var etUniversityEmail: TextInputEditText
    private lateinit var etCourse: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etVehicleColor: TextInputEditText
    private lateinit var etCarModel: TextInputEditText
    private lateinit var etCarRegistration: TextInputEditText
    private lateinit var autoCompleteInstitution: AutoCompleteTextView
    private lateinit var autoCompleteGender: AutoCompleteTextView
    private lateinit var btnSave: Button
    private lateinit var btnChangePassword: Button
    private lateinit var btnLogout: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_profile)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        initializeViews()
        setupSpinners()
        setupClickListeners()
        loadDriverData()
    }

    private fun initializeViews() {
        etFullName = findViewById(R.id.etFullName)
        etUniversityEmail = findViewById(R.id.etUniversityEmail)
        etCourse = findViewById(R.id.etCourse)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etVehicleColor = findViewById(R.id.etVehicleColor)
        etCarModel = findViewById(R.id.etCarModel)
        etCarRegistration = findViewById(R.id.etCarRegistration)
        autoCompleteInstitution = findViewById(R.id.autoCompleteInstitution)
        autoCompleteGender = findViewById(R.id.autoCompleteGender)
        btnSave = findViewById(R.id.btnSave)
        btnChangePassword = findViewById(R.id.btnChangePassword)
        btnLogout = findViewById(R.id.btnLogout)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
    }

    private fun setupSpinners() {
        // Institutions list
        val institutions = arrayOf(
            "University of Johannesburg",
            "University of Cape Town",
            "University of Pretoria",
            "Stellenbosch University",
            "University of the Witwatersrand",
            "North-West University",
            "University of KwaZulu-Natal",
            "Rhodes University"
        )

        val institutionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, institutions)
        autoCompleteInstitution.setAdapter(institutionAdapter)

        // Gender list
        val genders = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        autoCompleteGender.setAdapter(genderAdapter)
    }

    private fun setupClickListeners() {
        btnSave.setOnClickListener {
            updateDriverProfile()
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun loadDriverData() {
        val userId = auth.currentUser?.uid ?: return

        progressBar.visibility = android.view.View.VISIBLE

        db.collection("drivers").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Populate form fields with existing data
                    etFullName.setText(document.getString("fullName") ?: "")
                    etUniversityEmail.setText(document.getString("universityEmail") ?: "")
                    etCourse.setText(document.getString("course") ?: "")
                    etPhoneNumber.setText(document.getString("phoneNumber") ?: "")
                    etVehicleColor.setText(document.getString("vehicleColor") ?: "")
                    etCarModel.setText(document.getString("carModel") ?: "")
                    etCarRegistration.setText(document.getString("carRegistration") ?: "")

                    autoCompleteInstitution.setText(document.getString("institution") ?: "")
                    autoCompleteGender.setText(document.getString("gender") ?: "")

                    // Display status
                    val status = document.getString("status") ?: "pending"
                    tvStatus.text = "Account Status: ${status.replaceFirstChar { it.uppercase() }}"
                }
                progressBar.visibility = android.view.View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Failed to load profile data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateDriverProfile() {
        val userId = auth.currentUser?.uid ?: return

        val fullName = etFullName.text.toString().trim()
        val course = etCourse.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()
        val vehicleColor = etVehicleColor.text.toString().trim()
        val carModel = etCarModel.text.toString().trim()
        val carRegistration = etCarRegistration.text.toString().trim()
        val institution = autoCompleteInstitution.text.toString().trim()
        val gender = autoCompleteGender.text.toString().trim()

        // Validation
        if (fullName.isEmpty() || course.isEmpty() || phoneNumber.isEmpty() ||
            vehicleColor.isEmpty() || carModel.isEmpty() || carRegistration.isEmpty() ||
            institution.isEmpty() || gender.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        btnSave.isEnabled = false

        val updates = hashMapOf<String, Any>(
            "fullName" to fullName,
            "course" to course,
            "phoneNumber" to phoneNumber,
            "vehicleColor" to vehicleColor,
            "carModel" to carModel,
            "carRegistration" to carRegistration,
            "institution" to institution,
            "gender" to gender
        )

        db.collection("drivers").document(userId)
            .update(updates)
            .addOnSuccessListener {
                progressBar.visibility = android.view.View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                btnSave.isEnabled = true
                Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<TextInputEditText>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<TextInputEditText>(R.id.etConfirmPassword)

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Change") { dialogInterface, which ->
                changePassword(
                    etCurrentPassword.text.toString(),
                    etNewPassword.text.toString(),
                    etConfirmPassword.text.toString()
                )
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun changePassword(currentPassword: String, newPassword: String, confirmPassword: String) {
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Please fill in all password fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(this, "New passwords do not match", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser
        val email = user?.email

        if (email != null) {
            progressBar.visibility = android.view.View.VISIBLE

            // Re-authenticate user first
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential)
                .addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {
                        // Now update password
                        user.updatePassword(newPassword)
                            .addOnCompleteListener { updateTask ->
                                progressBar.visibility = android.view.View.GONE
                                if (updateTask.isSuccessful) {
                                    Toast.makeText(this, "Password updated successfully", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Failed to update password: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun logoutUser() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { dialogInterface, which ->
                auth.signOut()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }
}