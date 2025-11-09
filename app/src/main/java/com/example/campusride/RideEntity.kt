package com.example.campusride.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey val rideId: String,
    val passengerId: String,
    val driverId: String?,
    val status: String,
    val origin: String?,
    val destination: String?,
    val timestamp: Long,
    val synced: Boolean = false
)
