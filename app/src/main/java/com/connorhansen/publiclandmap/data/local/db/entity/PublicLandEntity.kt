package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "public_lands",
    indices = [
        Index("managing_agency"),
        Index("agency_category"),
        Index("state")
    ]
)
data class PublicLandEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "unit_name") val unitName: String,
    @ColumnInfo(name = "managing_agency") val managingAgency: String,
    @ColumnInfo(name = "agency_category") val agencyCategory: String?,
    @ColumnInfo(name = "designation_type") val designationType: String,
    @ColumnInfo(name = "public_access") val publicAccess: String?,
    val state: String,
    @ColumnInfo(name = "gis_acres") val gisAcres: Double?,
    @ColumnInfo(name = "centroid_lat") val centroidLat: Double?,
    @ColumnInfo(name = "centroid_lng") val centroidLng: Double?,
    val geojson: String?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long?
)
