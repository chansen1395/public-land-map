package com.connorhansen.publiclandmap.data.repository

import com.connorhansen.publiclandmap.data.local.db.dao.ParcelDao
import com.connorhansen.publiclandmap.data.mapper.ParcelMapper
import com.connorhansen.publiclandmap.data.remote.api.ParcelApi
import com.connorhansen.publiclandmap.domain.model.BoundingBox
import com.connorhansen.publiclandmap.domain.model.Parcel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParcelRepository @Inject constructor(
    private val parcelDao: ParcelDao,
    private val parcelApi: ParcelApi,
    private val parcelMapper: ParcelMapper
) {

    suspend fun getParcelsInBounds(bbox: BoundingBox): List<Parcel> {
        val local = parcelDao.getByBbox(bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng)
        if (local.isNotEmpty()) {
            return local.map { parcelMapper.entityToDomain(it) }
        }
        return try {
            val bboxStr = bbox.toBboxString()
            val response = parcelApi.getParcels(bboxStr, null)
            val entities = response.data.map { parcelMapper.dtoToEntity(it) }
            parcelDao.insertAll(entities)
            entities.map { parcelMapper.entityToDomain(it) }
        } catch (e: Exception) {
            local.map { parcelMapper.entityToDomain(it) }
        }
    }

    suspend fun searchParcels(query: String, state: String? = null): List<Parcel> {
        return try {
            val response = parcelApi.searchParcels(query, state)
            val entities = response.data.map { parcelMapper.dtoToEntity(it) }
            parcelDao.insertAll(entities)
            entities.map { parcelMapper.entityToDomain(it) }
        } catch (e: Exception) {
            parcelDao.searchByParcelNumber(query)
                .map { parcelMapper.entityToDomain(it) }
        }
    }

    suspend fun getParcelById(id: String): Parcel? {
        val local = parcelDao.getById(id)
        if (local != null) {
            return parcelMapper.entityToDomain(local)
        }
        return try {
            val dto = parcelApi.getParcelById(id)
            val entity = parcelMapper.dtoToEntity(dto)
            parcelDao.insertAll(listOf(entity))
            parcelMapper.entityToDomain(entity)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun refreshParcels(bbox: BoundingBox): List<Parcel> {
        return try {
            val bboxStr = bbox.toBboxString()
            val response = parcelApi.getParcels(bboxStr, null)
            val entities = response.data.map { parcelMapper.dtoToEntity(it) }
            parcelDao.insertAll(entities)
            entities.map { parcelMapper.entityToDomain(it) }
        } catch (e: Exception) {
            getParcelsInBounds(bbox)
        }
    }
}
