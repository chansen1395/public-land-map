package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.connorhansen.publiclandmap.data.local.db.entity.OfflineRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {

    @Query("SELECT * FROM offline_regions ORDER BY downloaded_at DESC")
    fun getAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions WHERE status = :status")
    suspend fun getByStatus(status: String): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun getById(id: String): OfflineRegionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: OfflineRegionEntity)

    @Query("UPDATE offline_regions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE offline_regions SET size_bytes = :sizeBytes WHERE id = :id")
    suspend fun updateSizeBytes(id: String, sizeBytes: Long)

    @Query("UPDATE offline_regions SET downloaded_at = :downloadedAt, status = :status WHERE id = :id")
    suspend fun markComplete(id: String, downloadedAt: Long, status: String = "complete")

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun delete(id: String)
}
