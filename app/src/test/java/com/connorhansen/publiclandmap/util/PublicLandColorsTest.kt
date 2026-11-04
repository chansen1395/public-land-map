package com.connorhansen.publiclandmap.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicLandColorsTest {

    @Test
    fun explicitAgencyCategoryWins() {
        assertEquals(
            PublicLandColors.CATEGORY_TRIBAL,
            PublicLandColors.getCategory("US Forest Service", agencyCategory = "Tribal")
        )
    }

    @Test
    fun blankAgencyCategoryIsIgnored() {
        assertEquals(
            PublicLandColors.CATEGORY_BLM,
            PublicLandColors.getCategory("US Bureau of Land Management", agencyCategory = " ")
        )
    }

    @Test
    fun federalAgenciesAreMatchedByName() {
        assertEquals(PublicLandColors.CATEGORY_USFS, PublicLandColors.getCategory("USDA Forest Service"))
        assertEquals(PublicLandColors.CATEGORY_BLM, PublicLandColors.getCategory("Bureau of Land Management"))
        assertEquals(PublicLandColors.CATEGORY_NPS, PublicLandColors.getCategory("National Park Service"))
        assertEquals(PublicLandColors.CATEGORY_USFWS, PublicLandColors.getCategory("US Fish and Wildlife Service"))
        assertEquals(PublicLandColors.CATEGORY_CORPS_RECLAMATION, PublicLandColors.getCategory("Army Corps of Engineers"))
        assertEquals(PublicLandColors.CATEGORY_DOE_DEFENSE, PublicLandColors.getCategory("Department of Energy"))
    }

    @Test
    fun stateAndLocalAgenciesAreMatchedByName() {
        assertEquals(PublicLandColors.CATEGORY_DNR_STATE_TRUST, PublicLandColors.getCategory("WA Department of Natural Resources"))
        assertEquals(PublicLandColors.CATEGORY_STATE_PARKS, PublicLandColors.getCategory("State Parks and Recreation Commission"))
        assertEquals(PublicLandColors.CATEGORY_STATE_WILDLIFE, PublicLandColors.getCategory("State Wildlife Area"))
        assertEquals(PublicLandColors.CATEGORY_LOCAL_GOVERNMENT, PublicLandColors.getCategory("County Parks"))
    }

    @Test
    fun fallbacksFollowAccessLevel() {
        assertEquals(PublicLandColors.CATEGORY_OPEN_ACCESS, PublicLandColors.getCategory("Unknown owner", publicAccess = "Open"))
        assertEquals(PublicLandColors.CATEGORY_OTHER_PUBLIC, PublicLandColors.getCategory("Unknown owner"))
    }

    @Test
    fun everyCategoryHasAColorAndHex() {
        val categories = PublicLandColors.legendItems.map { it.category }
        assertEquals(categories.size, categories.toSet().size)
        for (item in PublicLandColors.legendItems) {
            assertTrue(item.hex.matches(Regex("#[0-9A-F]{6}")))
            assertEquals(item.hex, PublicLandColors.getColorHex("x", agencyCategory = item.category))
            assertEquals(item.color, PublicLandColors.getColor("x", agencyCategory = item.category))
        }
    }

    @Test
    fun unknownCategoryFallsBackToGray() {
        assertEquals("#808080", PublicLandColors.getColorHex("x", agencyCategory = "Not a category"))
    }

    @Test
    fun mapStyleStopsHaveUniqueLabels() {
        val labels = PublicLandColors.mapStyleStops.map { it.first }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue(PublicLandColors.getMapLibreColorExpression().contains("\"match\""))
    }
}
