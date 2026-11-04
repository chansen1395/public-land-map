package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.connorhansen.publiclandmap.data.local.db.entity.TrackPointEntity

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE track_id = :trackId ORDER BY sequence ASC")
    suspend fun getByTrackId(trackId: String): List<TrackPointEntity>

    @Insert
    suspend fun insertBatch(points: List<TrackPointEntity>)

    @Insert
    suspend fun insert(point: TrackPointEntity)

    @Query("DELETE FROM track_points WHERE track_id = :trackId")
    suspend fun deleteByTrackId(trackId: String)

    @Query("SELECT COUNT(*) FROM track_points WHERE track_id = :trackId")
    suspend fun getPointCount(trackId: String): Int
}
