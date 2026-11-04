package com.connorhansen.publiclandmap.domain.model

data class Regulation(
    val id: Long,
    val areaId: String?,
    val areaName: String?,
    val regulationType: String,
    val managingAgency: String?,
    val state: String?,
    val title: String,
    val description: String?,
    val restrictionLevel: String?,
    val startDate: String?,
    val endDate: String?,
    val sourceUrl: String?
)
