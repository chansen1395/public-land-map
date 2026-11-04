package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "min_lat") val minLat: Double,
    @ColumnInfo(name = "min_lng") val minLng: Double,
    @ColumnInfo(name = "max_lat") val maxLat: Double,
    @ColumnInfo(name = "max_lng") val maxLng: Double,
    @ColumnInfo(name = "min_zoom") val minZoom: Int = 8,
    @ColumnInfo(name = "max_zoom") val maxZoom: Int = 15,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long?,
    @ColumnInfo(name = "downloaded_at") val downloadedAt: Long?,
    val status: String = "pending"
)
