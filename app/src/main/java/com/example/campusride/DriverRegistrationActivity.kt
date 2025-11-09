package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DriverRegistrationActivity : BaseActivity() {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var autoCompleteInstitution: AutoCompleteTextView
    private lateinit var autoCompleteGender: AutoCompleteTextView
    private lateinit var etFullName: TextInputEditText
    private lateinit var etUniversityEmail: TextInputEditText
    private lateinit var etCourse: TextInputEditText
    private lateinit var etPhoneNumber: TextInputEditText
    private lateinit var etVehicleColor: TextInputEditText
    private lateinit var etCarModel: TextInputEditText
    private lateinit var etCarRegistration: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar

    // Password layouts for showing/hiding
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_driver_registration)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupSpinners()
        setupClickListeners()
        checkForGoogleSignIn()

        // Pre-fill email if coming from login
        val prefilledEmail = intent.getStringExtra("prefilled_email")
        if (!prefilledEmail.isNullOrEmpty()) {
            etUniversityEmail.setText(prefilledEmail)
        }
    }

    private fun initializeViews() {
        autoCompleteInstitution = findViewById(R.id.autoCompleteInstitution)
        autoCompleteGender = findViewById(R.id.autoCompleteGender)
        etFullName = findViewById(R.id.etFullName)
        etUniversityEmail = findViewById(R.id.etUniversityEmail)
        etCourse = findViewById(R.id.etCourse)
        etPhoneNumber = findViewById(R.id.etPhoneNumber)
        etVehicleColor = findViewById(R.id.etVehicleColor)
        etCarModel = findViewById(R.id.etCarModel)
        etCarRegistration = findViewById(R.id.etCarRegistration)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)

        // FIXED: Get the TextInputLayout wrappers, not the EditTexts
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
    }

    private fun checkForGoogleSignIn() {
        handleGoogleSignInUser()
    }

    private fun handleGoogleSignInUser() {
        val fromGoogleSignIn = intent.getBooleanExtra("from_google_signin", false)
        if (fromGoogleSignIn) {
            val user = auth.currentUser
            // Pre-fill email from Google account
            user?.email?.let { email ->
                etUniversityEmail.setText(email)
                etUniversityEmail.isEnabled = false // Don't allow changing Google email
            }

            // Hide password fields for Google Sign-In users
            passwordLayout.visibility = android.view.View.GONE
            confirmPasswordLayout.visibility = android.view.View.GONE
        } else {
            // Make sure password fields are visible for regular registration
            passwordLayout.visibility = android.view.View.VISIBLE
            confirmPasswordLayout.visibility = android.view.View.VISIBLE
        }
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
            "Rosebank College",
            "Rhodes University"
        )

        val institutionAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, institutions)
        autoCompleteInstitution.setAdapter(institutionAdapter)
        autoCompleteInstitution.threshold = 1
        autoCompleteInstitution.setOnClickListener {
            autoCompleteInstitution.showDropDown()
        }

        // Gender list
        val genders = arrayOf("Male", "Female", "Other", "Prefer not to say")
        val genderAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        autoCompleteGender.setAdapter(genderAdapter)
        autoCompleteGender.threshold = 1
        autoCompleteGender.setOnClickListener {
            autoCompleteGender.showDropDown()
        }
    }

    private fun setupClickListeners() {
        // Register button
        btnRegister.setOnClickListener {
            registerDriver()
        }
    }

    private fun registerDriver() {
        val institution = autoCompleteInstitution.text.toString().trim()
        val fullName = etFullName.text.toString().trim()
        val universityEmail = etUniversityEmail.text.toString().trim()
        val course = etCourse.text.toString().trim()
        val phoneNumber = etPhoneNumber.text.toString().trim()
        val gender = autoCompleteGender.text.toString().trim()
        val vehicleColor = etVehicleColor.text.toString().trim()
        val carModel = etCarModel.text.toString().trim()
        val carRegistration = etCarRegistration.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()

        val fromGoogleSignIn = intent.getBooleanExtra("from_google_signin", false)

        // Validation (adjust for Google Sign-In)
        if (institution.isEmpty() || fullName.isEmpty() || universityEmail.isEmpty() ||
            course.isEmpty() || phoneNumber.isEmpty() || gender.isEmpty() ||
            vehicleColor.isEmpty() || carModel.isEmpty() || carRegistration.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // For non-Google users, validate password
        if (!fromGoogleSignIn) {
            if (password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return
            }
            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters long", Toast.LENGTH_SHORT).show()
                return
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // Validate email format
        if (!isValidUniversityEmail(universityEmail)) {
            Toast.makeText(this, "Please use a valid university email address", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        btnRegister.isEnabled = false

        if (fromGoogleSignIn) {
            // User already authenticated via Google, just save to Firestore
            val userId = auth.currentUser?.uid ?: ""
            if (userId.isNotEmpty()) {
                saveDriverToFirestore(userId, institution, fullName, universityEmail, course,
                    phoneNumber, gender, vehicleColor, carModel, carRegistration)
            } else {
                progressBar.visibility = android.view.View.GONE
                btnRegister.isEnabled = true
                Toast.makeText(this, "User not authenticated", Toast.LENGTH_LONG).show()
            }
        } else {
            // Traditional email/password registration
            auth.createUserWithEmailAndPassword(universityEmail, password)
                .addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        val userId = auth.currentUser?.uid ?: ""
                        if (userId.isNotEmpty()) {
                            saveDriverToFirestore(userId, institution, fullName, universityEmail, course,
                                phoneNumber, gender, vehicleColor, carModel, carRegistration)
                        } else {
                            progressBar.visibility = android.view.View.GONE
                            btnRegister.isEnabled = true
                            Toast.makeText(this, "User ID not found", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        progressBar.visibility = android.view.View.GONE
                        btnRegister.isEnabled = true
                        Toast.makeText(this, "Registration failed: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    progressBar.visibility = android.view.View.GONE
                    btnRegister.isEnabled = true
                    Toast.makeText(this, "Registration error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun isValidUniversityEmail(email: String): Boolean {
        val universityDomains = listOf(
            "@uj.ac.za", "@uct.ac.za", "@up.ac.za", "@sun.ac.za",
            "@wits.ac.za", "@nwu.ac.za", "@ukzn.ac.za", "@ru.ac.za",
            "@rosebankcollege.co.za", "@rcconnect.edu.za", "@gmail.com"
        )
        return universityDomains.any { email.endsWith(it, ignoreCase = true) }
    }

    private fun saveDriverToFirestore(
        userId: String,
        institution: String,
        fullName: String,
        universityEmail: String,
        course: String,
        phoneNumber: String,
        gender: String,
        vehicleColor: String,
        carModel: String,
        carRegistration: String
    ) {
        val driver = hashMapOf(
            "userId" to userId,
            "institution" to institution,
            "fullName" to fullName,
            "universityEmail" to universityEmail,
            "course" to course,
            "phoneNumber" to phoneNumber,
            "gender" to gender,
            "vehicleColor" to vehicleColor,
            "carModel" to carModel,
            "carRegistration" to carRegistration,
            "userType" to "driver",
            "registrationDate" to com.google.firebase.Timestamp.now(),
            "status" to "approved",
            "onlineStatus" to false,
            "completedRides" to 0,
            "totalEarnings" to 0.0,
            "rating" to 0.0
        )

        db.collection("drivers").document(userId)
            .set(driver)
            .addOnSuccessListener {
                progressBar.visibility = android.view.View.GONE
                Toast.makeText(this, "Registration successful! Welcome to CampusRide.", Toast.LENGTH_LONG).show()

                // Navigate directly to driver dashboard
                val intent = Intent(this, DriverDashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                btnRegister.isEnabled = true
                Toast.makeText(this, "Failed to save driver data: ${e.message}", Toast.LENGTH_LONG).show()

                // Delete the user from auth if Firestore save fails (only for email/password users)
                if (!intent.getBooleanExtra("from_google_signin", false)) {
                    auth.currentUser?.delete()?.addOnCompleteListener {
                        Toast.makeText(this, "Registration rolled back due to database error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }
}