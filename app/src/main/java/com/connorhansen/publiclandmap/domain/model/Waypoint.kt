package com.connorhansen.publiclandmap.domain.model

data class Waypoint(
    val id: String,
    val name: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val icon: String = "pin",
    val color: String = "#FF0000",
    val createdAt: Long,
    val updatedAt: Long?
)
