package com.example.campusride

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.campusride.persistence.AppDatabase
import com.example.campusride.persistence.RideEntity
import com.example.campusride.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

object LocalSyncHelper {

    fun saveRideToHistory(
        context: Context,
        passengerId: String,
        driverId: String?,
        status: String,
        origin: String?,
        destination: String?,
        fare: Double? = null,
        distance: Double? = null,
        optionalRideId: String? = null
    ) {
        val rideId = optionalRideId ?: UUID.randomUUID().toString()
        val ride = RideEntity(
            rideId = rideId,
            passengerId = passengerId,
            driverId = driverId,
            status = status,
            origin = origin,
            destination = destination,
            timestamp = System.currentTimeMillis(),
            synced = false
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context.applicationContext)
                db.rideDao().insert(ride)
                Log.d("LocalSync", "Successfully saved ride to local history: $rideId, Status: $status")
            } catch (e: Exception) {
                Log.e("LocalSync", "Failed to save ride to local database: ${e.message}")
            }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(workRequest)
    }
}