package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.TrackEntity
import com.connorhansen.publiclandmap.data.local.db.entity.TrackPointEntity
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.TrackPoint
import javax.inject.Inject

class TrackMapper @Inject constructor() {

    fun entityToDomain(entity: TrackEntity, pointEntities: List<TrackPointEntity>): Track {
        return Track(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            distanceMeters = entity.distanceMeters,
            elevationGain = entity.elevationGain,
            durationMs = entity.durationMs,
            createdAt = entity.createdAt,
            points = pointEntities.map { pointEntityToDomain(it) }
        )
    }

    fun entityToDomainWithoutPoints(entity: TrackEntity): Track {
        return Track(
            id = entity.id,
            name = entity.name,
            description = entity.description,
            distanceMeters = entity.distanceMeters,
            elevationGain = entity.elevationGain,
            durationMs = entity.durationMs,
            createdAt = entity.createdAt,
            points = emptyList()
        )
    }

    fun domainToEntity(domain: Track): TrackEntity {
        return TrackEntity(
            id = domain.id,
            name = domain.name,
            description = domain.description,
            distanceMeters = domain.distanceMeters,
            elevationGain = domain.elevationGain,
            durationMs = domain.durationMs,
            createdAt = domain.createdAt
        )
    }

    fun pointEntityToDomain(entity: TrackPointEntity): TrackPoint {
        return TrackPoint(
            latitude = entity.latitude,
            longitude = entity.longitude,
            elevation = entity.elevation,
            timestamp = entity.timestamp,
            accuracy = entity.accuracy,
            sequence = entity.sequence
        )
    }

    fun pointDomainToEntity(trackId: String, domain: TrackPoint): TrackPointEntity {
        return TrackPointEntity(
            trackId = trackId,
            latitude = domain.latitude,
            longitude = domain.longitude,
            elevation = domain.elevation,
            timestamp = domain.timestamp,
            accuracy = domain.accuracy,
            sequence = domain.sequence
        )
    }
}
