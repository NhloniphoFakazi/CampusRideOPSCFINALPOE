package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.SignInButton
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : BaseActivity() {

    // Firebase
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    // UI Components
    private lateinit var etUniversityEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnDriverLogin: MaterialButton
    private lateinit var btnPassengerLogin: MaterialButton
    private lateinit var btnGoogleSignIn: SignInButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvSignUp: TextView
    private lateinit var btnBack: ImageButton
    private lateinit var progressBar: ProgressBar

    private var selectedUserType: String = "driver" // Default to driver

    // Google Sign-In result launcher
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("GOOGLE_SIGNIN", "Google Sign-In result code: ${result.resultCode}")

        if (result.resultCode == RESULT_OK) {
            Log.d("GOOGLE_SIGNIN", "Google Sign-In successful, processing account...")
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                Log.d("GOOGLE_SIGNIN", "Google account obtained: ${account?.email}")
                if (account != null && account.idToken != null) {
                    firebaseAuthWithGoogle(account.idToken!!)
                } else {
                    progressBar.visibility = android.view.View.GONE
                    enableAllButtons()
                    Toast.makeText(this, "Google sign in failed: Invalid account", Toast.LENGTH_LONG).show()
                }
            } catch (e: ApiException) {
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()
                Log.e("GOOGLE_SIGNIN", "Google sign in failed: ${e.statusCode} - ${e.message}")
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            progressBar.visibility = android.view.View.GONE
            enableAllButtons()
            Log.d("GOOGLE_SIGNIN", "Google Sign-In cancelled or failed")
            Toast.makeText(this, "Google Sign-In cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        initializeViews()
        setupClickListeners()
        setupBackPressedHandler()
        checkForRegistrationSuccess()
    }

    private fun initializeViews() {
        etUniversityEmail = findViewById(R.id.etUniversityEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnDriverLogin = findViewById(R.id.btnDriverLogin)
        btnPassengerLogin = findViewById(R.id.btnPassengerLogin)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvSignUp = findViewById(R.id.tvSignUp)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)

        // Set default user type selection
        updateUserTypeSelection()

        // Pre-fill email if coming from registration
        val prefilledEmail = intent.getStringExtra("prefilled_email")
        if (!prefilledEmail.isNullOrEmpty()) {
            etUniversityEmail.setText(prefilledEmail)
        }
    }

    private fun checkForRegistrationSuccess() {
        // Check for registration success message
        if (intent.getBooleanExtra("registration_success", false)) {
            val userType = intent.getStringExtra("user_type")
            val message = intent.getStringExtra("message") ?: "Registration successful!"

            // Show appropriate message based on user type
            when (userType) {
                "driver" -> {
                    Toast.makeText(this, "Driver $message", Toast.LENGTH_LONG).show()
                    selectedUserType = "driver"
                    updateUserTypeSelection()
                }
                "passenger" -> {
                    Toast.makeText(this, "Passenger $message", Toast.LENGTH_LONG).show()
                    selectedUserType = "passenger"
                    updateUserTypeSelection()
                }
                else -> {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }

            // Pre-fill email if available
            val prefilledEmail = intent.getStringExtra("prefilled_email")
            if (!prefilledEmail.isNullOrEmpty()) {
                etUniversityEmail.setText(prefilledEmail)
            }
        }
    }

    private fun setupClickListeners() {
        // Back button
        btnBack.setOnClickListener {
            navigateToUserSelection()
        }

        // User type selection
        btnDriverLogin.setOnClickListener {
            selectedUserType = "driver"
            updateUserTypeSelection()
        }

        btnPassengerLogin.setOnClickListener {
            selectedUserType = "passenger"
            updateUserTypeSelection()
        }

        // Login button
        btnLogin.setOnClickListener {
            loginWithEmailPassword()
        }

        // Google Sign-In button
        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        // Forgot password
        tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }

        // Sign up link
        tvSignUp.setOnClickListener {
            navigateToUserSelection()
        }
    }

    private fun setupBackPressedHandler() {
        // Handle back button press with the new API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToUserSelection()
            }
        })
    }

    private fun signInWithGoogle() {
        try {
            progressBar.visibility = android.view.View.VISIBLE
            disableAllButtons()

            // Clear any previous sign-in attempts
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }.addOnFailureListener {
                // If sign-out fails, still try to sign in
                val signInIntent = googleSignInClient.signInIntent
                googleSignInLauncher.launch(signInIntent)
            }
        } catch (e: Exception) {
            progressBar.visibility = android.view.View.GONE
            enableAllButtons()
            Log.e("GOOGLE_SIGNIN", "Error starting Google Sign-In: ${e.message}")
            Toast.makeText(this, "Error starting Google Sign-In", Toast.LENGTH_SHORT).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Check if this is a new user
                    val user = auth.currentUser
                    val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

                    Log.d("GOOGLE_SIGNIN", "Google sign-in successful. New user: $isNewUser")

                    if (isNewUser && user != null) {
                        // New user - redirect to appropriate registration
                        Log.d("GOOGLE_SIGNIN", "Redirecting new user to registration")
                        redirectToRegistration(user.email ?: "")
                    } else {
                        // Existing user - check user type and navigate
                        Log.d("GOOGLE_SIGNIN", "Checking user type for existing user")
                        checkUserTypeAndNavigate(user?.uid)
                    }
                } else {
                    progressBar.visibility = android.view.View.GONE
                    enableAllButtons()
                    val errorMessage = task.exception?.message ?: "Unknown error"
                    Log.e("GOOGLE_SIGNIN", "Authentication failed: $errorMessage")
                    Toast.makeText(
                        baseContext, "Google sign-in failed: $errorMessage",
                        Toast.LENGTH_LONG
                    ).show()

                    // Sign out if authentication failed
                    auth.signOut()
                    googleSignInClient.signOut()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()
                Log.e("GOOGLE_SIGNIN", "Sign-in failure: ${e.message}")
                Toast.makeText(this, "Google sign-in error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun redirectToRegistration(email: String) {
        val intent = when (selectedUserType) {
            "driver" -> Intent(this, DriverRegistrationActivity::class.java)
            else -> Intent(this, PassengerRegistrationActivity::class.java)
        }.apply {
            putExtra("prefilled_email", email)
            putExtra("from_google_signin", true)
        }
        startActivity(intent)
        finish()
    }

    private fun updateUserTypeSelection() {
        if (selectedUserType == "driver") {
            btnDriverLogin.setBackgroundColor(getColor(R.color.primary))
            btnPassengerLogin.setBackgroundColor(getColor(R.color.gray))
            // Update hint text based on user type
            etUniversityEmail.hint = "Driver University Email"
        } else {
            btnDriverLogin.setBackgroundColor(getColor(R.color.gray))
            btnPassengerLogin.setBackgroundColor(getColor(R.color.primary))
            // Update hint text based on user type
            etUniversityEmail.hint = "Passenger University Email"
        }
    }

    private fun loginWithEmailPassword() {
        val email = etUniversityEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        // Validation
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        disableAllButtons()

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, check user type and navigate accordingly
                    val user = auth.currentUser
                    checkUserTypeAndNavigate(user?.uid)
                } else {
                    // If sign in fails, display a message to the user.
                    progressBar.visibility = android.view.View.GONE
                    enableAllButtons()
                    Toast.makeText(
                        baseContext, "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()
                Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkUserTypeAndNavigate(userId: String?) {
        if (userId == null) {
            progressBar.visibility = android.view.View.GONE
            enableAllButtons()
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("USER_CHECK", "Checking user type for ID: $userId")

        // Check if user is a driver
        db.collection("drivers").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val status = document.getString("status") ?: "pending"
                    val userType = document.getString("userType") ?: "driver"

                    Log.d("USER_CHECK", "Driver document found. Status: $status, Type: $userType")

                    if (userType == "driver" && (status == "approved" || status == "active")) {
                        // Navigate to Driver Dashboard
                        Log.d("USER_CHECK", "Navigating to Driver Dashboard")
                        navigateToDriverDashboard()
                    } else if (userType == "driver" && status != "approved") {
                        progressBar.visibility = android.view.View.GONE
                        enableAllButtons()
                        Toast.makeText(
                            this,
                            "Your driver account is $status. Please wait for approval.",
                            Toast.LENGTH_LONG
                        ).show()
                        // Sign out since account is not approved
                        auth.signOut()
                        googleSignInClient.signOut()
                    } else {
                        // User exists but type mismatch
                        handleUserTypeMismatch()
                    }
                } else {
                    // Check if user is a passenger
                    db.collection("passengers").document(userId).get()
                        .addOnSuccessListener { passengerDocument ->
                            if (passengerDocument.exists()) {
                                val userType = passengerDocument.getString("userType") ?: "passenger"

                                Log.d("USER_CHECK", "Passenger document found. Type: $userType")

                                if (userType == "passenger") {
                                    // Navigate to Passenger Dashboard
                                    Log.d("USER_CHECK", "Navigating to Passenger Dashboard")
                                    navigateToPassengerDashboard()
                                } else {
                                    handleUserTypeMismatch()
                                }
                            } else {
                                progressBar.visibility = android.view.View.GONE
                                enableAllButtons()
                                Log.d("USER_CHECK", "User not found in any collection")
                                Toast.makeText(
                                    this,
                                    "User account not found. Please register first.",
                                    Toast.LENGTH_LONG
                                ).show()
                                // Sign out since user doesn't exist in our database
                                auth.signOut()
                                googleSignInClient.signOut()
                            }
                        }
                        .addOnFailureListener {
                            progressBar.visibility = android.view.View.GONE
                            enableAllButtons()
                            Log.e("USER_CHECK", "Error checking passenger data")
                            Toast.makeText(this, "Error checking user data", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()
                Log.e("USER_CHECK", "Error checking driver data")
                Toast.makeText(this, "Error checking user data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleUserTypeMismatch() {
        progressBar.visibility = android.view.View.GONE
        enableAllButtons()
        Toast.makeText(
            this,
            "Account type mismatch. Please login with the correct user type.",
            Toast.LENGTH_LONG
        ).show()
        auth.signOut()
        googleSignInClient.signOut()
    }

    private fun navigateToDriverDashboard() {
        progressBar.visibility = android.view.View.GONE
        enableAllButtons()
        val intent = Intent(this, DriverDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToPassengerDashboard() {
        progressBar.visibility = android.view.View.GONE
        enableAllButtons()
        val intent = Intent(this, PassengerDashboardActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToUserSelection() {
        val intent = Intent(this, UserTypeSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showForgotPasswordDialog() {
        val email = etUniversityEmail.text.toString().trim()

        if (email.isEmpty()) {
            Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        disableAllButtons()

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()

                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                enableAllButtons()
                Toast.makeText(this, "Password reset error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun disableAllButtons() {
        btnLogin.isEnabled = false
        btnDriverLogin.isEnabled = false
        btnPassengerLogin.isEnabled = false
        btnGoogleSignIn.isEnabled = false
    }

    private fun enableAllButtons() {
        btnLogin.isEnabled = true
        btnDriverLogin.isEnabled = true
        btnPassengerLogin.isEnabled = true
        btnGoogleSignIn.isEnabled = true
    }
}