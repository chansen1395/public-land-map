package com.connorhansen.publiclandmap.data.repository

import com.connorhansen.publiclandmap.data.local.db.dao.PublicLandDao
import com.connorhansen.publiclandmap.data.mapper.PublicLandMapper
import com.connorhansen.publiclandmap.data.remote.api.PublicLandApi
import com.connorhansen.publiclandmap.domain.model.BoundingBox
import com.connorhansen.publiclandmap.domain.model.PublicLand
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PublicLandRepository @Inject constructor(
    private val publicLandDao: PublicLandDao,
    private val publicLandApi: PublicLandApi,
    private val publicLandMapper: PublicLandMapper
) {

    suspend fun getPublicLandsInBounds(bbox: BoundingBox): List<PublicLand> {
        val local = publicLandDao.getByBbox(bbox.minLat, bbox.maxLat, bbox.minLng, bbox.maxLng)
        if (local.isNotEmpty()) {
            return local.map { publicLandMapper.entityToDomain(it) }
        }
        return try {
            val bboxStr = bbox.toBboxString()
            val response = publicLandApi.getPublicLands(bboxStr)
            val entities = response.data.map { publicLandMapper.dtoToEntity(it) }
            publicLandDao.insertAll(entities)
            entities.map { publicLandMapper.entityToDomain(it) }
        } catch (e: Exception) {
            local.map { publicLandMapper.entityToDomain(it) }
        }
    }

    suspend fun getPublicLandById(id: String): PublicLand? {
        val local = publicLandDao.getById(id)
        if (local != null) {
            return publicLandMapper.entityToDomain(local)
        }
        return try {
            val dto = publicLandApi.getById(id)
            val entity = publicLandMapper.dtoToEntity(dto)
            publicLandDao.insertAll(listOf(entity))
            publicLandMapper.entityToDomain(entity)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchPublicLands(query: String, state: String? = null): List<PublicLand> {
        return try {
            val response = publicLandApi.searchPublicLands(query, state)
            val entities = response.data.map { publicLandMapper.dtoToEntity(it) }
            publicLandDao.insertAll(entities)
            entities.map { publicLandMapper.entityToDomain(it) }
        } catch (e: Exception) {
            publicLandDao.searchByName(query).map { publicLandMapper.entityToDomain(it) }
        }
    }

    suspend fun getByAgency(agency: String): List<PublicLand> {
        return publicLandDao.getByAgency(agency).map { publicLandMapper.entityToDomain(it) }
    }

    suspend fun refreshPublicLands(bbox: BoundingBox): List<PublicLand> {
        return try {
            val bboxStr = bbox.toBboxString()
            val response = publicLandApi.getPublicLands(bboxStr)
            val entities = response.data.map { publicLandMapper.dtoToEntity(it) }
            publicLandDao.insertAll(entities)
            entities.map { publicLandMapper.entityToDomain(it) }
        } catch (e: Exception) {
            getPublicLandsInBounds(bbox)
        }
    }
}
