package com.example.campusride

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.*

class BookRideActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // UI Components
    private lateinit var etPickupLocation: AutoCompleteTextView
    private lateinit var etDropoffLocation: AutoCompleteTextView
    private lateinit var autoCompleteVehicleType: AutoCompleteTextView
    private lateinit var tvEstimatedFare: TextView
    private lateinit var tvEstimatedTime: TextView
    private lateinit var btnBookRide: Button
    private lateinit var btnCurrentLocation: Button
    private lateinit var progressBar: ProgressBar

    // Ride details
    private var estimatedFare = 0.0
    private var estimatedTime = 0
    private var selectedVehicleType = "Standard"

    // Coordinates
    private var pickupLatitude = 0.0
    private var pickupLongitude = 0.0
    private var dropoffLatitude = 0.0
    private var dropoffLongitude = 0.0

    // Track if we're currently processing a location change
    private var isProcessingLocationChange = false

    // Activity result launchers
    private val pickupLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val locationName = data.getStringExtra(MapSelectionActivity.EXTRA_LOCATION_NAME) ?: ""
                val latitude = data.getDoubleExtra(MapSelectionActivity.EXTRA_LATITUDE, 0.0)
                val longitude = data.getDoubleExtra(MapSelectionActivity.EXTRA_LONGITUDE, 0.0)

                etPickupLocation.setText(locationName)
                pickupLatitude = latitude
                pickupLongitude = longitude

                Log.d("BookRide", "Pickup location set: $locationName ($latitude, $longitude)")

                // Trigger fare calculation immediately
                calculateFareAndTime()
            }
        }
    }

    private val dropoffLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                val locationName = data.getStringExtra(MapSelectionActivity.EXTRA_LOCATION_NAME) ?: ""
                val latitude = data.getDoubleExtra(MapSelectionActivity.EXTRA_LATITUDE, 0.0)
                val longitude = data.getDoubleExtra(MapSelectionActivity.EXTRA_LONGITUDE, 0.0)

                etDropoffLocation.setText(locationName)
                dropoffLatitude = latitude
                dropoffLongitude = longitude

                Log.d("BookRide", "Dropoff location set: $locationName ($latitude, $longitude)")

                // Trigger fare calculation immediately
                calculateFareAndTime()
            }
        }
    }

    // Optional fallback predefined campus spots
    private val campusLocations = mapOf(
        "Main Campus Gate" to Pair(-26.1908, 28.0307),
        "Student Center" to Pair(-26.1915, 28.0312),
        "Library" to Pair(-26.1920, 28.0320),
        "Engineering Block" to Pair(-26.1930, 28.0335),
        "Cafeteria" to Pair(-26.1910, 28.0315),
        "Science Building" to Pair(-26.1925, 28.0328),
        "Sports Complex" to Pair(-26.1895, 28.0295),
        "Residence A" to Pair(-26.1940, 28.0340),
        "Residence B" to Pair(-26.1945, 28.0345),
        "Admin Building" to Pair(-26.1900, 28.0300)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_book_ride)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        initializeViews()
        setupVehicleTypeDropdown()
        setupTextWatchers()
        setupClickListeners()
        restoreSavedInputs()
    }

    private fun initializeViews() {
        etPickupLocation = findViewById(R.id.etPickupLocation)
        etDropoffLocation = findViewById(R.id.etDropoffLocation)
        autoCompleteVehicleType = findViewById(R.id.autoCompleteVehicleType)
        tvEstimatedFare = findViewById(R.id.tvEstimatedFare)
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime)
        btnBookRide = findViewById(R.id.btnBookRide)
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation)
        progressBar = findViewById(R.id.progressBar)

        // Set click listeners for location fields to open map selection
        etPickupLocation.setOnClickListener {
            openMapSelection(true)
        }

        etDropoffLocation.setOnClickListener {
            openMapSelection(false)
        }

        // Set up autocomplete for campus locations
        setupLocationAutocomplete()
    }

    private fun setupLocationAutocomplete() {
        val campusLocationNames = campusLocations.keys.toTypedArray()
        val locationAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, campusLocationNames)

        etPickupLocation.setAdapter(locationAdapter)
        etDropoffLocation.setAdapter(locationAdapter)

        etPickupLocation.threshold = 1
        etDropoffLocation.threshold = 1

        // Set item click listeners for autocomplete
        etPickupLocation.setOnItemClickListener { _, _, position, _ ->
            val selectedLocation = campusLocationNames[position]
            setLocationCoordinates(selectedLocation, true)
            // Trigger fare calculation immediately after autocomplete selection
            calculateFareAndTime()
        }

        etDropoffLocation.setOnItemClickListener { _, _, position, _ ->
            val selectedLocation = campusLocationNames[position]
            setLocationCoordinates(selectedLocation, false)
            // Trigger fare calculation immediately after autocomplete selection
            calculateFareAndTime()
        }
    }

    private fun openMapSelection(isPickup: Boolean) {
        val intent = Intent(this, MapSelectionActivity::class.java)
        if (isPickup) {
            pickupLocationLauncher.launch(intent)
        } else {
            dropoffLocationLauncher.launch(intent)
        }
    }

    private fun setupVehicleTypeDropdown() {
        val vehicleTypes = arrayOf("Standard", "Premium", "Group")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, vehicleTypes)
        autoCompleteVehicleType.setAdapter(adapter)

        autoCompleteVehicleType.setOnItemClickListener { _, _, position, _ ->
            selectedVehicleType = vehicleTypes[position]
            Log.d("BookRide", "Vehicle type changed to: $selectedVehicleType")
            calculateFareAndTime() // Recalculate fare when vehicle type changes
        }

        autoCompleteVehicleType.setText(vehicleTypes[0], false)
    }

    private fun setupTextWatchers() {
        etPickupLocation.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val location = s.toString().trim()
                if (location.isNotEmpty() && !isProcessingLocationChange) {
                    isProcessingLocationChange = true
                    handleLocationInput(location, true)
                    isProcessingLocationChange = false
                } else if (location.isEmpty()) {
                    pickupLatitude = 0.0
                    pickupLongitude = 0.0
                    resetEstimates()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etDropoffLocation.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val location = s.toString().trim()
                if (location.isNotEmpty() && !isProcessingLocationChange) {
                    isProcessingLocationChange = true
                    handleLocationInput(location, false)
                    isProcessingLocationChange = false
                } else if (location.isEmpty()) {
                    dropoffLatitude = 0.0
                    dropoffLongitude = 0.0
                    resetEstimates()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun handleLocationInput(location: String, isPickup: Boolean) {
        Log.d("BookRide", "Handling location input: $location (isPickup: $isPickup)")

        if (campusLocations.containsKey(location)) {
            setLocationCoordinates(location, isPickup)
            calculateFareAndTime()
        } else {
            // Only do OSM lookup if coordinates aren't already set from map selection
            if ((isPickup && pickupLatitude == 0.0) || (!isPickup && dropoffLatitude == 0.0)) {
                getCoordinatesFromOSM(location, isPickup)
            } else {
                // If we have coordinates but the name changed, still recalculate
                calculateFareAndTime()
            }
        }
    }

    private fun setupClickListeners() {
        btnCurrentLocation.setOnClickListener {
            // Set current location to a default campus location
            val currentLocation = "Student Center"
            etPickupLocation.setText(currentLocation)
            setLocationCoordinates(currentLocation, true)
            calculateFareAndTime()
            Toast.makeText(this, "Current location set to Student Center", Toast.LENGTH_SHORT).show()
        }

        btnBookRide.setOnClickListener { bookRide() }
    }

    private fun setLocationCoordinates(name: String, isPickup: Boolean) {
        campusLocations[name]?.let { (lat, lon) ->
            if (isPickup) {
                pickupLatitude = lat
                pickupLongitude = lon
                Log.d("BookRide", "Set pickup coordinates: ($lat, $lon)")
            } else {
                dropoffLatitude = lat
                dropoffLongitude = lon
                Log.d("BookRide", "Set dropoff coordinates: ($lat, $lon)")
            }
        }
    }

    private fun getCoordinatesFromOSM(locationName: String, isPickup: Boolean) {
        Log.d("BookRide", "Fetching OSM coordinates for: $locationName")

        Thread {
            try {
                val client = OkHttpClient()
                val encodedLocation = java.net.URLEncoder.encode(locationName, "UTF-8")
                val url = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedLocation&limit=1"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "CampusRideApp/1.0")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (!body.isNullOrEmpty()) {
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 0) {
                        val first = jsonArray.getJSONObject(0)
                        val lat = first.getDouble("lat")
                        val lon = first.getDouble("lon")
                        val displayName = first.getString("display_name")

                        runOnUiThread {
                            if (isPickup) {
                                pickupLatitude = lat
                                pickupLongitude = lon
                                Log.d("BookRide", "OSM pickup coordinates: ($lat, $lon)")
                            } else {
                                dropoffLatitude = lat
                                dropoffLongitude = lon
                                Log.d("BookRide", "OSM dropoff coordinates: ($lat, $lon)")
                            }
                            calculateFareAndTime()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this, "Location '$locationName' not found", Toast.LENGTH_SHORT).show()
                            // Reset coordinates if not found
                            if (isPickup) {
                                pickupLatitude = 0.0
                                pickupLongitude = 0.0
                            } else {
                                dropoffLatitude = 0.0
                                dropoffLongitude = 0.0
                            }
                            resetEstimates()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to find location: ${e.message}", Toast.LENGTH_SHORT).show()
                    Log.e("OSM", "Error fetching coordinates", e)
                    // Reset coordinates on error
                    if (isPickup) {
                        pickupLatitude = 0.0
                        pickupLongitude = 0.0
                    } else {
                        dropoffLatitude = 0.0
                        dropoffLongitude = 0.0
                    }
                    resetEstimates()
                }
            }
        }.start()
    }

    private fun calculateFareAndTime() {
        Log.d("BookRide", "Calculating fare and time...")
        Log.d("BookRide", "Pickup: ($pickupLatitude, $pickupLongitude)")
        Log.d("BookRide", "Dropoff: ($dropoffLatitude, $dropoffLongitude)")

        if (pickupLatitude == 0.0 || pickupLongitude == 0.0 ||
            dropoffLatitude == 0.0 || dropoffLongitude == 0.0) {
            Log.d("BookRide", "Missing coordinates, resetting estimates")
            resetEstimates()
            return
        }

        val distance = calculateDistance(pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude)
        Log.d("BookRide", "Calculated distance: ${"%.2f".format(distance)} km")

        val baseFare = when (selectedVehicleType) {
            "Premium" -> 40.0
            "Group" -> 60.0
            else -> 25.0
        }

        val distanceCost = distance * 5.0 // R5 per km
        estimatedFare = baseFare + distanceCost
        estimatedTime = (distance / 0.5).toInt().coerceIn(3, 120) // Assuming 30km/h average speed

        Log.d("BookRide", "Final calculation - Fare: R$estimatedFare, Time: $estimatedTime min")
        updateEstimatesUI()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in kilometers
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    private fun resetEstimates() {
        estimatedFare = 0.0
        estimatedTime = 0
        updateEstimatesUI()
    }

    private fun updateEstimatesUI() {
        tvEstimatedFare.text = "R${"%.2f".format(estimatedFare)}"
        tvEstimatedTime.text = if (estimatedTime > 0) "$estimatedTime min" else "--"

        val valid = pickupLatitude != 0.0 && pickupLongitude != 0.0 &&
                dropoffLatitude != 0.0 && dropoffLongitude != 0.0 &&
                estimatedFare > 0

        btnBookRide.isEnabled = valid
        btnBookRide.alpha = if (valid) 1f else 0.5f

        Log.d("BookRide", "UI Updated - Fare: ${tvEstimatedFare.text}, Time: ${tvEstimatedTime.text}, Valid: $valid")
    }

    private fun bookRide() {
        val pickup = etPickupLocation.text.toString().trim()
        val dropoff = etDropoffLocation.text.toString().trim()

        if (pickup.isEmpty() || dropoff.isEmpty()) {
            Toast.makeText(this, "Please enter both pickup and dropoff locations", Toast.LENGTH_SHORT).show()
            return
        }

        if (pickup == dropoff) {
            Toast.makeText(this, "Pickup and dropoff locations cannot be the same", Toast.LENGTH_SHORT).show()
            return
        }

        if (estimatedFare <= 0) {
            Toast.makeText(this, "Please select valid locations to calculate fare", Toast.LENGTH_SHORT).show()
            return
        }

        val user = auth.currentUser ?: run {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        val rideId = UUID.randomUUID().toString()
        val distance = calculateDistance(pickupLatitude, pickupLongitude, dropoffLatitude, dropoffLongitude)

        val rideData = hashMapOf(
            "rideId" to rideId,
            "passengerId" to user.uid,
            "passengerName" to (user.displayName ?: user.email ?: "Passenger"),
            "passengerEmail" to (user.email ?: ""),
            "pickupLocation" to pickup,
            "pickupLatitude" to pickupLatitude,
            "pickupLongitude" to pickupLongitude,
            "dropoffLocation" to dropoff,
            "dropoffLatitude" to dropoffLatitude,
            "dropoffLongitude" to dropoffLongitude,
            "vehicleType" to selectedVehicleType,
            "estimatedFare" to estimatedFare,
            "estimatedTime" to estimatedTime,
            "distance" to distance,
            "status" to "searching", // Initial status
            "driverId" to "", // Will be set when driver accepts
            "driverName" to "",
            "driverPhone" to "",
            "vehicleModel" to "",
            "vehicleColor" to "",
            "vehicleRegistration" to "",
            "createdAt" to Timestamp.now(),
            "updatedAt" to Timestamp.now()
        )

        progressBar.visibility = View.VISIBLE
        btnBookRide.isEnabled = false

        Log.d("BookRide", "Booking ride with data: $rideData")

        db.collection("rides").document(rideId).set(rideData)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Ride booked successfully! Searching for drivers...", Toast.LENGTH_LONG).show()
                saveInputs(pickup, dropoff)

                // Navigate to ride tracking
                val intent = Intent(this, RideTrackingActivity::class.java)
                intent.putExtra("rideId", rideId)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnBookRide.isEnabled = true
                Toast.makeText(this, "Booking failed: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("BookRide", "Booking error", e)
            }
    }

    private fun saveInputs(pickup: String, dropoff: String) {
        val prefs = getSharedPreferences("ridePrefs", MODE_PRIVATE)
        prefs.edit()
            .putString("pickup", pickup)
            .putString("dropoff", dropoff)
            .putString("vehicleType", selectedVehicleType)
            .apply()
    }

    private fun restoreSavedInputs() {
        val prefs = getSharedPreferences("ridePrefs", MODE_PRIVATE)
        etPickupLocation.setText(prefs.getString("pickup", ""))
        etDropoffLocation.setText(prefs.getString("dropoff", ""))
        autoCompleteVehicleType.setText(prefs.getString("vehicleType", "Standard"), false)

        // If we have saved locations, try to set coordinates and calculate fare
        val savedPickup = prefs.getString("pickup", "")
        val savedDropoff = prefs.getString("dropoff", "")

        if (!savedPickup.isNullOrEmpty() && campusLocations.containsKey(savedPickup)) {
            setLocationCoordinates(savedPickup, true)
        }

        if (!savedDropoff.isNullOrEmpty() && campusLocations.containsKey(savedDropoff)) {
            setLocationCoordinates(savedDropoff, false)
        }

        // Calculate fare if we have both coordinates
        if (pickupLatitude != 0.0 && dropoffLatitude != 0.0) {
            calculateFareAndTime()
        }
    }
}