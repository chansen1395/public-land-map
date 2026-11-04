package com.connorhansen.publiclandmap.data.repository

import com.connorhansen.publiclandmap.data.local.db.dao.RegulationDao
import com.connorhansen.publiclandmap.data.mapper.RegulationMapper
import com.connorhansen.publiclandmap.data.remote.api.RegulationApi
import com.connorhansen.publiclandmap.domain.model.BoundingBox
import com.connorhansen.publiclandmap.domain.model.Regulation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegulationRepository @Inject constructor(
    private val regulationDao: RegulationDao,
    private val regulationApi: RegulationApi,
    private val regulationMapper: RegulationMapper
) {

    suspend fun getRegulationsForArea(areaId: String): List<Regulation> {
        val local = regulationDao.getByAreaId(areaId)
        if (local.isNotEmpty()) {
            return local.map { regulationMapper.entityToDomain(it) }
        }
        return try {
            val dtos = regulationApi.getForArea(areaId)
            val entities = dtos.map { regulationMapper.dtoToEntity(it) }
            regulationDao.insertAll(entities)
            entities.map { regulationMapper.entityToDomain(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getRegulationsInBounds(bbox: BoundingBox, type: String? = null): List<Regulation> {
        return try {
            val bboxStr = bbox.toBboxString()
            val response = regulationApi.getByBbox(bboxStr, type)
            val entities = response.data.map { regulationMapper.dtoToEntity(it) }
            regulationDao.insertAll(entities)
            entities.map { regulationMapper.entityToDomain(it) }
        } catch (e: Exception) {
            val currentDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val local = if (type != null) {
                regulationDao.getByType(type)
            } else {
                regulationDao.getActive(currentDate)
            }
            local.map { regulationMapper.entityToDomain(it) }
        }
    }
}
