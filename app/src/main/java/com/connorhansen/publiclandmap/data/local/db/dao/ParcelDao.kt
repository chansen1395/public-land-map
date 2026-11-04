package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.connorhansen.publiclandmap.data.local.db.entity.ParcelEntity

@Dao
interface ParcelDao {

    @Query(
        """SELECT * FROM parcels 
        WHERE bbox_min_lat <= :maxLat AND bbox_max_lat >= :minLat 
        AND bbox_min_lng <= :maxLng AND bbox_max_lng >= :minLng 
        LIMIT :limit"""
    )
    suspend fun getByBbox(
        minLat: Double,
        maxLat: Double,
        minLng: Double,
        maxLng: Double,
        limit: Int = 500
    ): List<ParcelEntity>

    @Query("SELECT * FROM parcels WHERE county = :county AND state = :state")
    suspend fun getByCounty(county: String, state: String): List<ParcelEntity>

    @Query("SELECT * FROM parcels WHERE parcel_number LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchByParcelNumber(query: String, limit: Int = 50): List<ParcelEntity>

    @Query("SELECT * FROM parcels WHERE id = :id")
    suspend fun getById(id: String): ParcelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parcels: List<ParcelEntity>)

    @Query("DELETE FROM parcels WHERE county = :county AND state = :state")
    suspend fun deleteByCounty(county: String, state: String)

    @Query("DELETE FROM parcels")
    suspend fun deleteAll()
}
