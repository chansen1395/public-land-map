package com.connorhansen.publiclandmap.data.repository

import com.connorhansen.publiclandmap.data.local.db.dao.WaypointDao
import com.connorhansen.publiclandmap.data.mapper.WaypointMapper
import com.connorhansen.publiclandmap.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaypointRepository @Inject constructor(
    private val waypointDao: WaypointDao,
    private val waypointMapper: WaypointMapper
) {

    fun getAllWaypoints(): Flow<List<Waypoint>> {
        return waypointDao.getAll().map { entities ->
            entities.map { waypointMapper.entityToDomain(it) }
        }
    }

    suspend fun getWaypointById(id: String): Waypoint? {
        return waypointDao.getById(id)?.let { waypointMapper.entityToDomain(it) }
    }

    suspend fun saveWaypoint(waypoint: Waypoint) {
        waypointDao.insert(waypointMapper.domainToEntity(waypoint))
    }

    suspend fun updateWaypoint(waypoint: Waypoint) {
        waypointDao.update(waypointMapper.domainToEntity(waypoint))
    }

    suspend fun deleteWaypoint(id: String) {
        waypointDao.deleteById(id)
    }
}
