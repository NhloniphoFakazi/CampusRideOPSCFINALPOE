package com.example.campusride.persistence

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ride: RideEntity)

    @Update
    suspend fun update(ride: RideEntity)

    @Query("SELECT * FROM rides ORDER BY timestamp DESC")
    fun getAllRidesFlow(): Flow<List<RideEntity>>

    @Query("SELECT * FROM rides WHERE synced = 0")
    suspend fun getUnsyncedRides(): List<RideEntity>

    @Query("SELECT * FROM rides WHERE rideId = :id LIMIT 1")
    suspend fun getRideById(id: String): RideEntity?
}
