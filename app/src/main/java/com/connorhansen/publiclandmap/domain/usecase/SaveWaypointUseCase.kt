package com.connorhansen.publiclandmap.domain.usecase

import com.connorhansen.publiclandmap.data.repository.WaypointRepository
import com.connorhansen.publiclandmap.domain.model.Waypoint
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class SaveWaypointUseCase @Inject constructor(
    private val waypointRepository: WaypointRepository
) {
    suspend operator fun invoke(waypoint: Waypoint) {
        val existing = waypointRepository.getWaypointById(waypoint.id)
        if (existing != null) {
            waypointRepository.updateWaypoint(
                waypoint.copy(updatedAt = System.currentTimeMillis())
            )
        } else {
            waypointRepository.saveWaypoint(waypoint)
        }
    }

    suspend fun create(
        name: String,
        latitude: Double,
        longitude: Double,
        description: String? = null,
        elevation: Double? = null,
        icon: String = "pin",
        color: String = "#FF0000"
    ): Waypoint {
        val waypoint = Waypoint(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            icon = icon,
            color = color,
            createdAt = System.currentTimeMillis(),
            updatedAt = null
        )
        waypointRepository.saveWaypoint(waypoint)
        return waypoint
    }

    suspend fun delete(id: String) {
        waypointRepository.deleteWaypoint(id)
    }

    fun getAll(): Flow<List<Waypoint>> {
        return waypointRepository.getAllWaypoints()
    }

    suspend fun getById(id: String): Waypoint? {
        return waypointRepository.getWaypointById(id)
    }
}
