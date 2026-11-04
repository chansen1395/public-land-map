package com.connorhansen.publiclandmap.data.mapper

import com.connorhansen.publiclandmap.data.local.db.entity.WaypointEntity
import com.connorhansen.publiclandmap.domain.model.BoundingBox
import com.connorhansen.publiclandmap.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Test

class WaypointMapperTest {

    private val mapper = WaypointMapper()

    @Test
    fun domainToEntityAndBackKeepsEveryField() {
        val waypoint = Waypoint(
            id = "w1",
            name = "Camp",
            description = "Flat, near water",
            latitude = 46.85,
            longitude = -121.76,
            elevation = 1_650.0,
            icon = "tent",
            color = "#00FF00",
            createdAt = 100L,
            updatedAt = 200L
        )
        val entity: WaypointEntity = mapper.domainToEntity(waypoint)
        assertEquals("w1", entity.id)
        assertEquals("tent", entity.icon)
        assertEquals(waypoint, mapper.entityToDomain(entity))
    }

    @Test
    fun nullableFieldsStayNull() {
        val waypoint = Waypoint("w2", "Pin", null, 1.0, 2.0, null, createdAt = 1L, updatedAt = null)
        assertEquals(waypoint, mapper.entityToDomain(mapper.domainToEntity(waypoint)))
    }

    @Test
    fun boundingBoxStringIsWestSouthEastNorth() {
        // The backend expects west,south,east,north
        assertEquals("-122.5,47.0,-122.0,47.5", BoundingBox(47.0, -122.5, 47.5, -122.0).toBboxString())
    }
}
