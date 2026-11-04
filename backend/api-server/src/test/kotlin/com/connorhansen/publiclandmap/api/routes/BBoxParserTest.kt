package com.connorhansen.publiclandmap.api.routes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BBoxParserTest {

    @Test
    fun parsesWestSouthEastNorth() {
        val bbox = parseBbox("-122.5,47.0,-122.0,47.5")
        assertNotNull(bbox)
        assertEquals(BBox(west = -122.5, south = 47.0, east = -122.0, north = 47.5), bbox)
    }

    @Test
    fun trimsWhitespaceAroundValues() {
        assertNotNull(parseBbox(" -122.5 , 47.0 , -122.0 , 47.5 "))
    }

    @Test
    fun rejectsWrongNumberOfValues() {
        assertNull(parseBbox(""))
        assertNull(parseBbox("1,2,3"))
        assertNull(parseBbox("1,2,3,4,5"))
    }

    @Test
    fun rejectsNonNumbers() {
        assertNull(parseBbox("a,b,c,d"))
        assertNull(parseBbox("-122.5,47.0,-122.0,"))
    }

    @Test
    fun rejectsOutOfRangeCoordinates() {
        assertNull(parseBbox("-181,47.0,-122.0,47.5"))
        assertNull(parseBbox("-122.5,47.0,181,47.5"))
        assertNull(parseBbox("-122.5,-91,-122.0,47.5"))
        assertNull(parseBbox("-122.5,47.0,-122.0,91"))
    }

    @Test
    fun rejectsEmptyOrReversedBoxes() {
        assertNull(parseBbox("-122.0,47.0,-122.5,47.5"))
        assertNull(parseBbox("-122.5,47.5,-122.0,47.0"))
        assertNull(parseBbox("-122.5,47.0,-122.5,47.5"))
        assertNull(parseBbox("-122.5,47.0,-122.0,47.0"))
    }
}
