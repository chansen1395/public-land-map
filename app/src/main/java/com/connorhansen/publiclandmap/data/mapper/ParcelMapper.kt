package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.ParcelEntity
import com.connorhansen.publiclandmap.data.remote.dto.ParcelDto
import com.connorhansen.publiclandmap.domain.model.Parcel
import javax.inject.Inject

class ParcelMapper @Inject constructor() {

    fun dtoToEntity(dto: ParcelDto): ParcelEntity {
        return ParcelEntity(
            id = dto.id,
            parcelNumber = dto.parcelNumber,
            ownerName = dto.ownerName,
            county = dto.county,
            state = dto.state,
            acreage = dto.acreage,
            landUseCode = dto.landUseCode,
            landUseDesc = dto.landUseDesc,
            centroidLat = dto.centroidLat,
            centroidLng = dto.centroidLng,
            bboxMinLat = null,
            bboxMinLng = null,
            bboxMaxLat = null,
            bboxMaxLng = null,
            geojson = dto.geometry,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun entityToDomain(entity: ParcelEntity): Parcel {
        return Parcel(
            id = entity.id,
            parcelNumber = entity.parcelNumber,
            ownerName = entity.ownerName,
            county = entity.county,
            state = entity.state,
            acreage = entity.acreage,
            landUseCode = entity.landUseCode,
            landUseDesc = entity.landUseDesc,
            centroidLat = entity.centroidLat,
            centroidLng = entity.centroidLng,
            geojson = entity.geojson
        )
    }

    fun dtoToDomain(dto: ParcelDto): Parcel {
        return Parcel(
            id = dto.id,
            parcelNumber = dto.parcelNumber,
            ownerName = dto.ownerName,
            county = dto.county,
            state = dto.state,
            acreage = dto.acreage,
            landUseCode = dto.landUseCode,
            landUseDesc = dto.landUseDesc,
            centroidLat = dto.centroidLat,
            centroidLng = dto.centroidLng,
            geojson = dto.geometry
        )
    }
}
