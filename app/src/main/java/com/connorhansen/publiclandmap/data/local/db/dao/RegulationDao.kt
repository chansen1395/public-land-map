package com.connorhansen.publiclandmap.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.connorhansen.publiclandmap.data.local.db.entity.RegulationEntity

@Dao
interface RegulationDao {

    @Query("SELECT * FROM regulations WHERE area_id = :areaId")
    suspend fun getByAreaId(areaId: String): List<RegulationEntity>

    @Query("SELECT * FROM regulations WHERE regulation_type = :type")
    suspend fun getByType(type: String): List<RegulationEntity>

    @Query(
        """SELECT * FROM regulations 
        WHERE (end_date IS NULL OR end_date >= :currentDate)
        AND (start_date IS NULL OR start_date <= :currentDate)"""
    )
    suspend fun getActive(currentDate: String): List<RegulationEntity>

    @Query("SELECT * FROM regulations WHERE state = :state")
    suspend fun getByState(state: String): List<RegulationEntity>

    @Query("SELECT * FROM regulations WHERE id = :id")
    suspend fun getById(id: Long): RegulationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(regulations: List<RegulationEntity>)

    @Query("DELETE FROM regulations")
    suspend fun deleteAll()
}
