package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parcels",
    indices = [
        Index("county", "state"),
        Index("parcel_number"),
        Index("bbox_min_lat", "bbox_max_lat", "bbox_min_lng", "bbox_max_lng")
    ]
)
data class ParcelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "parcel_number") val parcelNumber: String,
    @ColumnInfo(name = "owner_name") val ownerName: String?,
    val county: String,
    val state: String,
    val acreage: Double?,
    @ColumnInfo(name = "land_use_code") val landUseCode: String?,
    @ColumnInfo(name = "land_use_desc") val landUseDesc: String?,
    @ColumnInfo(name = "centroid_lat") val centroidLat: Double,
    @ColumnInfo(name = "centroid_lng") val centroidLng: Double,
    @ColumnInfo(name = "bbox_min_lat") val bboxMinLat: Double?,
    @ColumnInfo(name = "bbox_min_lng") val bboxMinLng: Double?,
    @ColumnInfo(name = "bbox_max_lat") val bboxMaxLat: Double?,
    @ColumnInfo(name = "bbox_max_lng") val bboxMaxLng: Double?,
    val geojson: String?,
    @ColumnInfo(name = "last_updated") val lastUpdated: Long?
)
