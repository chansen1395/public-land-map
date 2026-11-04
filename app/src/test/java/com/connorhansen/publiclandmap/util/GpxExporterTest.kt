package com.connorhansen.publiclandmap.util

import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.TrackPoint
import com.connorhansen.publiclandmap.domain.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxExporterTest {

    private val exporter = GpxExporter()

    // 2026-01-02T03:04:05Z
    private val timestamp = 1_767_323_045_000L

    private val waypoint = Waypoint(
        id = "w1",
        name = "Trailhead <north> & \"parking\"",
        description = "It's gated",
        latitude = 46.85,
        longitude = -121.76,
        elevation = 1_650.5,
        icon = "pin",
        createdAt = timestamp,
        updatedAt = null
    )

    private val track = Track(
        id = "t1",
        name = "Ridge loop",
        description = null,
        distanceMeters = 1_200.0,
        elevationGain = 80.0,
        durationMs = 3_600_000,
        createdAt = timestamp,
        points = listOf(
            TrackPoint(46.85, -121.76, 1_650.0, timestamp, 5f, 0),
            TrackPoint(46.86, -121.77, null, timestamp + 60_000, 5f, 1)
        )
    )

    @Test
    fun emptyDocumentHasHeaderMetadataAndFooter() {
        val gpx = exporter.buildGpx("export", timestamp, emptyList(), emptyList())
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<gpx version=\"1.1\" creator=\"Public Land Map\""))
        assertTrue(gpx.contains("<name>export</name>"))
        assertTrue(gpx.contains("<time>2026-01-02T03:04:05Z</time>"))
        assertTrue(gpx.endsWith("</gpx>"))
        assertFalse(gpx.contains("<wpt"))
        assertFalse(gpx.contains("<trk>"))
    }

    @Test
    fun waypointFieldsAreWrittenAndEscaped() {
        val gpx = exporter.buildGpx("export", timestamp, listOf(waypoint), emptyList())
        assertTrue(gpx.contains("<wpt lat=\"46.85\" lon=\"-121.76\">"))
        assertTrue(gpx.contains("<ele>1650.5</ele>"))
        assertTrue(gpx.contains("<name>Trailhead &lt;north&gt; &amp; &quot;parking&quot;</name>"))
        assertTrue(gpx.contains("<desc>It&apos;s gated</desc>"))
        assertTrue(gpx.contains("<sym>pin</sym>"))
    }

    @Test
    fun trackPointsAreWrittenInOrderAndOptionalElevationIsSkipped() {
        val gpx = exporter.buildGpx("export", timestamp, emptyList(), listOf(track))
        assertEquals(1, Regex("<trk>").findAll(gpx).count())
        assertEquals(2, Regex("<trkpt ").findAll(gpx).count())
        assertEquals(1, Regex("<ele>").findAll(gpx).count())
        assertTrue(gpx.indexOf("lat=\"46.85\"") < gpx.indexOf("lat=\"46.86\""))
        assertTrue(gpx.contains("<time>2026-01-02T03:05:05Z</time>"))
        assertFalse(gpx.contains("<desc>"))
    }

    @Test
    fun waypointsComeBeforeTracks() {
        val gpx = exporter.buildGpx("export", timestamp, listOf(waypoint), listOf(track))
        assertTrue(gpx.indexOf("<wpt") < gpx.indexOf("<trk>"))
    }
}
