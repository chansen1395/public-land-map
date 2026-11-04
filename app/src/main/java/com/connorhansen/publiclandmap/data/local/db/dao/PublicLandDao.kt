package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.connorhansen.publiclandmap.data.local.db.entity.PublicLandEntity

@Dao
interface PublicLandDao {

    @Query(
        """SELECT * FROM public_lands 
        WHERE centroid_lat IS NOT NULL AND centroid_lng IS NOT NULL
        AND centroid_lat BETWEEN :minLat AND :maxLat 
        AND centroid_lng BETWEEN :minLng AND :maxLng 
        LIMIT :limit"""
    )
    suspend fun getByBbox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int = 500
    ): List<PublicLandEntity>

    @Query("SELECT * FROM public_lands WHERE managing_agency = :agency")
    suspend fun getByAgency(agency: String): List<PublicLandEntity>

    @Query("SELECT * FROM public_lands WHERE state = :state")
    suspend fun getByState(state: String): List<PublicLandEntity>

    @Query("SELECT * FROM public_lands WHERE id = :id")
    suspend fun getById(id: String): PublicLandEntity?

    @Query("SELECT * FROM public_lands WHERE unit_name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchByName(query: String, limit: Int = 50): List<PublicLandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lands: List<PublicLandEntity>)

    @Query("DELETE FROM public_lands")
    suspend fun deleteAll()
}
