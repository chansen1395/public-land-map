package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "distance_meters") val distanceMeters: Double?,
    @ColumnInfo(name = "elevation_gain") val elevationGain: Double?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
