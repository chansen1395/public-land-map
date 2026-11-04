package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.OfflineRegionEntity
import com.connorhansen.publiclandmap.data.remote.dto.OfflineRegionDto
import com.connorhansen.publiclandmap.domain.model.OfflineRegion
import javax.inject.Inject

class OfflineRegionMapper @Inject constructor() {

    fun entityToDomain(entity: OfflineRegionEntity): OfflineRegion {
        return OfflineRegion(
            id = entity.id,
            name = entity.name,
            minLat = entity.minLat,
            minLng = entity.minLng,
            maxLat = entity.maxLat,
            maxLng = entity.maxLng,
            minZoom = entity.minZoom,
            maxZoom = entity.maxZoom,
            sizeBytes = entity.sizeBytes,
            downloadedAt = entity.downloadedAt,
            status = entity.status
        )
    }

    fun domainToEntity(domain: OfflineRegion): OfflineRegionEntity {
        return OfflineRegionEntity(
            id = domain.id,
            name = domain.name,
            minLat = domain.minLat,
            minLng = domain.minLng,
            maxLat = domain.maxLat,
            maxLng = domain.maxLng,
            minZoom = domain.minZoom,
            maxZoom = domain.maxZoom,
            sizeBytes = domain.sizeBytes,
            downloadedAt = domain.downloadedAt,
            status = domain.status
        )
    }

    fun dtoToEntity(dto: OfflineRegionDto): OfflineRegionEntity {
        return OfflineRegionEntity(
            id = dto.id,
            name = dto.name,
            minLat = dto.minLat,
            minLng = dto.minLng,
            maxLat = dto.maxLat,
            maxLng = dto.maxLng,
            minZoom = dto.minZoom,
            maxZoom = dto.maxZoom,
            sizeBytes = dto.sizeBytes,
            downloadedAt = null,
            status = "pending"
        )
    }

    fun dtoToDomain(dto: OfflineRegionDto): OfflineRegion {
        return OfflineRegion(
            id = dto.id,
            name = dto.name,
            minLat = dto.minLat,
            minLng = dto.minLng,
            maxLat = dto.maxLat,
            maxLng = dto.maxLng,
            minZoom = dto.minZoom,
            maxZoom = dto.maxZoom,
            sizeBytes = dto.sizeBytes,
            downloadedAt = null,
            status = "pending"
        )
    }
}
