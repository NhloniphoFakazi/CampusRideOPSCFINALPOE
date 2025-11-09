package com.example.campusride.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.campusride.persistence.AppDatabase
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        try {
            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.rideDao()
            val unsynced = dao.getUnsyncedRides()
            if (unsynced.isEmpty()) {
                Log.d(TAG, "No unsynced rides found.")
                return Result.success()
            }

            val firestore = FirebaseFirestore.getInstance()

            for (ride in unsynced) {
                try {
                    val data = hashMapOf<String, Any?>(
                        "rideId" to ride.rideId,
                        "passengerId" to ride.passengerId,
                        "driverId" to ride.driverId,
                        "status" to ride.status,
                        "origin" to ride.origin,
                        "destination" to ride.destination,
                        "timestamp" to Timestamp(ride.timestamp / 1000, ((ride.timestamp % 1000) * 1000000).toInt())
                    )
                    // synchronous wait (safe on IO thread)
                    val task = firestore.collection("rides").document(ride.rideId).set(data)
                    Tasks.await(task) // will throw on failure -> caught below

                    // mark local as synced
                    dao.update(ride.copy(synced = true))
                    Log.d(TAG, "Synced ride ${ride.rideId}")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to upload ride ${ride.rideId}: ${e.message}")
                    // Retry later
                    return Result.retry()
                }
            }

            return Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "SyncWorker error: ${t.message}")
            return Result.retry()
        }
    }
}