package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.RegulationEntity
import com.connorhansen.publiclandmap.data.remote.dto.RegulationDto
import com.connorhansen.publiclandmap.domain.model.Regulation
import javax.inject.Inject

class RegulationMapper @Inject constructor() {

    fun dtoToEntity(dto: RegulationDto): RegulationEntity {
        return RegulationEntity(
            id = dto.id ?: 0,
            areaId = dto.areaId,
            areaName = dto.areaName,
            regulationType = dto.regulationType,
            managingAgency = dto.managingAgency,
            state = dto.state,
            title = dto.title,
            description = dto.description,
            restrictionLevel = dto.restrictionLevel,
            startDate = dto.startDate,
            endDate = dto.endDate,
            geojson = dto.geometry,
            sourceUrl = dto.sourceUrl,
            lastUpdated = System.currentTimeMillis()
        )
    }

    fun entityToDomain(entity: RegulationEntity): Regulation {
        return Regulation(
            id = entity.id,
            areaId = entity.areaId,
            areaName = entity.areaName,
            regulationType = entity.regulationType,
            managingAgency = entity.managingAgency,
            state = entity.state,
            title = entity.title,
            description = entity.description,
            restrictionLevel = entity.restrictionLevel,
            startDate = entity.startDate,
            endDate = entity.endDate,
            sourceUrl = entity.sourceUrl
        )
    }
}
