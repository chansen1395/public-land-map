package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "regulations",
    indices = [
        Index("regulation_type"),
        Index("area_id")
    ]
)
data class RegulationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "area_id") val areaId: String?,
    @ColumnInfo(name = "area_name") val areaName: String?,
    @ColumnInfo(name = "regulation_type") val regulationType: String,
    @ColumnInfo(name = "managing_agency") val managingAgency: String?,
    val state: String?,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "restriction_level") val restrictionLevel: String?,
    @ColumnInfo(name = "start_date") val startDate: String?,
    @ColumnInfo(name = "end_date") val endDate: String?,
    val geojson: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long?
)
