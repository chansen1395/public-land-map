package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.WaypointEntity
import com.connorhansen.publiclandmap.domain.model.Waypoint
import javax.inject.Inject

class WaypointMapper @Inject constructor() {

    fun entityToDomain(entity: WaypointEntity): Waypoint {
        return Waypoint(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            latitude = entity.latitude,
            longitude = entity.longitude,
            elevation = entity.elevation,
            icon = entity.icon,
            color = entity.color,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun domainToEntity(domain: Waypoint): WaypointEntity {
        return WaypointEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            latitude = domain.latitude,
            longitude = domain.longitude,
            elevation = domain.elevation,
            icon = domain.icon,
            color = domain.color,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
