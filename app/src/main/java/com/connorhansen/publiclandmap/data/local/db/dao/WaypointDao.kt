package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.connorhansen.publiclandmap.data.local.db.entity.WaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints ORDER BY created_at DESC")
    fun getAll(): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: String): WaypointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: WaypointEntity)

    @Update
    suspend fun update(waypoint: WaypointEntity)

    @Delete
    suspend fun delete(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun deleteById(id: String)
}
