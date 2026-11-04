package com.connorhansen.publiclandmap.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "track_points",
    indices = [
        Index("track_id", "sequence")
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val timestamp: Long,
    val accuracy: Float?,
    val sequence: Int
)
