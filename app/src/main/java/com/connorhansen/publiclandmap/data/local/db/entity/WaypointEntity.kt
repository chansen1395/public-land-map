package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class WaypointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val icon: String = "pin",
    val color: String = "#FF0000",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?
)
