package com.example.campusride

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.*

class MapSelectionActivity : BaseActivity() {

    private lateinit var mapView: MapView
    private lateinit var etSearchLocation: TextInputEditText
    private lateinit var tvSelectedLocation: TextView
    private lateinit var tvCoordinates: TextView
    private lateinit var btnConfirmLocation: Button
    private lateinit var fabCurrentLocation: FloatingActionButton

    private var selectedLocation: GeoPoint? = null
    private var selectedLocationName: String = ""
    private lateinit var currentMarker: Marker

    companion object {
        const val EXTRA_SELECTED_LOCATION = "selected_location"
        const val EXTRA_LOCATION_NAME = "location_name"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map_selection)

        // Initialize OSMDroid
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        initializeViews()
        setupMap()
        setupClickListeners()
    }

    private fun initializeViews() {
        mapView = findViewById(R.id.mapView)
        etSearchLocation = findViewById(R.id.etSearchLocation)
        tvSelectedLocation = findViewById(R.id.tvSelectedLocation)
        tvCoordinates = findViewById(R.id.tvCoordinates)
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation)
        fabCurrentLocation = findViewById(R.id.fabCurrentLocation)

        // Set up toolbar
        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Set default location (Johannesburg)
        val defaultLocation = GeoPoint(-26.2041, 28.0473)
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(defaultLocation)

        // Add click listener for map
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                handleMapTap(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean {
                return false
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(mapEventsOverlay)

        // Initialize marker
        currentMarker = Marker(mapView)
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    }

    private fun setupClickListeners() {
        btnConfirmLocation.setOnClickListener {
            confirmLocationSelection()
        }

        fabCurrentLocation.setOnClickListener {
            // For demo purposes, center on a default location
            // In a real app, you would use GPS to get actual current location
            val currentLocation = GeoPoint(-26.2041, 28.0473) // Johannesburg coordinates
            mapView.controller.animateTo(currentLocation)
            handleMapTap(currentLocation)
        }

        etSearchLocation.setOnEditorActionListener { v, actionId, event ->
            val query = etSearchLocation.text.toString().trim()
            if (query.isNotEmpty()) {
                searchLocation(query)
            }
            true
        }
    }

    private fun handleMapTap(point: GeoPoint) {
        selectedLocation = point
        updateLocationInfo(point)
        addMarkerToMap(point)
    }

    private fun updateLocationInfo(point: GeoPoint) {
        // Get address from coordinates (reverse geocoding)
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                selectedLocationName = address.getAddressLine(0) ?: "Unknown Location"

                // Try to get a more specific name
                val thoroughfare = address.thoroughfare
                val featureName = address.featureName
                val locality = address.locality

                selectedLocationName = when {
                    !thoroughfare.isNullOrEmpty() -> thoroughfare
                    !featureName.isNullOrEmpty() -> featureName
                    !locality.isNullOrEmpty() -> locality
                    else -> selectedLocationName
                }
            } else {
                selectedLocationName = "Selected Location"
            }
        } catch (e: Exception) {
            selectedLocationName = "Selected Location"
        }

        tvSelectedLocation.text = selectedLocationName
        tvCoordinates.text = "Lat: ${"%.6f".format(point.latitude)}, Lng: ${"%.6f".format(point.longitude)}"

        btnConfirmLocation.isEnabled = true
    }

    private fun addMarkerToMap(point: GeoPoint) {
        mapView.overlays.remove(currentMarker)

        currentMarker = Marker(mapView)
        currentMarker.position = point
        currentMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        currentMarker.title = selectedLocationName

        mapView.overlays.add(currentMarker)
        mapView.invalidate()

        // Center map on selected location
        mapView.controller.animateTo(point)
    }

    private fun searchLocation(query: String) {
        // Simple implementation - in a real app, use proper geocoding service
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(query, 1)
            if (addresses?.isNotEmpty() == true) {
                val address = addresses[0]
                val location = GeoPoint(address.latitude, address.longitude)

                mapView.controller.animateTo(location)
                handleMapTap(location)

                etSearchLocation.setText(address.getAddressLine(0))
            } else {
                // Fallback to predefined campus locations
                searchCampusLocations(query)
            }
        } catch (e: Exception) {
            searchCampusLocations(query)
        }
    }

    private fun searchCampusLocations(query: String) {
        val campusLocations = mapOf(
            "Main Campus Gate" to GeoPoint(-26.1908, 28.0307),
            "Student Center" to GeoPoint(-26.1915, 28.0312),
            "Library" to GeoPoint(-26.1920, 28.0320),
            "Science Building" to GeoPoint(-26.1925, 28.0328),
            "Engineering Block" to GeoPoint(-26.1930, 28.0335),
            "Sports Complex" to GeoPoint(-26.1895, 28.0295),
            "Residence A" to GeoPoint(-26.1940, 28.0340),
            "Residence B" to GeoPoint(-26.1945, 28.0345),
            "Cafeteria" to GeoPoint(-26.1910, 28.0315),
            "Admin Building" to GeoPoint(-26.1900, 28.0300)
        )

        val location = campusLocations.entries.find {
            it.key.contains(query, ignoreCase = true)
        }?.value

        if (location != null) {
            mapView.controller.animateTo(location)
            handleMapTap(location)
            etSearchLocation.setText(campusLocations.entries.find { it.value == location }?.key)
        } else {
            // Show message if no location found
            tvSelectedLocation.text = "Location not found"
            tvCoordinates.text = "Try a different search term"
            btnConfirmLocation.isEnabled = false
        }
    }

    private fun confirmLocationSelection() {
        selectedLocation?.let { location ->
            val resultIntent = Intent().apply {
                putExtra(EXTRA_SELECTED_LOCATION, selectedLocationName)
                putExtra(EXTRA_LOCATION_NAME, selectedLocationName)
                putExtra(EXTRA_LATITUDE, location.latitude)
                putExtra(EXTRA_LONGITUDE, location.longitude)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}