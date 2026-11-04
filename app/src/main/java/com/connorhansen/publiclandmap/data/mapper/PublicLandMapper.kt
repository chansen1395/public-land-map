package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.PublicLandEntity
import com.connorhansen.publiclandmap.data.remote.dto.PublicLandDto
import com.connorhansen.publiclandmap.domain.model.PublicLand
import javax.inject.Inject

class PublicLandMapper @Inject constructor() {

    fun dtoToEntity(dto: PublicLandDto): PublicLandEntity {
        return PublicLandEntity(
            id = dto.id,
            unitName = dto.unitName,
            managingAgency = dto.managingAgency,
            agencyCategory = dto.agencyCategory,
            designationType = dto.designationType,
            publicAccess = dto.publicAccess,
            state = dto.state,
            gisAcres = dto.gisAcres,
            centroidLat = dto.centroidLat,
            centroidLng = dto.centroidLng,
            geojson = dto.geometry,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun entityToDomain(entity: PublicLandEntity): PublicLand {
        return PublicLand(
            id = entity.id,
            unitName = entity.unitName,
            managingAgency = entity.managingAgency,
            agencyCategory = entity.agencyCategory,
            designationType = entity.designationType,
            publicAccess = entity.publicAccess,
            state = entity.state,
            gisAcres = entity.gisAcres,
            centroidLat = entity.centroidLat,
            centroidLng = entity.centroidLng,
            geojson = entity.geojson
        )
    }

    fun dtoToDomain(dto: PublicLandDto): PublicLand {
        return PublicLand(
            id = dto.id,
            unitName = dto.unitName,
            managingAgency = dto.managingAgency,
            agencyCategory = dto.agencyCategory,
            designationType = dto.designationType,
            publicAccess = dto.publicAccess,
            state = dto.state,
            gisAcres = dto.gisAcres,
            centroidLat = dto.centroidLat,
            centroidLng = dto.centroidLng,
            geojson = dto.geometry
        )
    }
}
