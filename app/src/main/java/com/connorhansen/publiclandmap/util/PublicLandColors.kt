package com.connorhansen.publiclandmap.util

import androidx.compose.ui.graphics.Color
import com.connorhansen.publiclandmap.ui.theme.ArmyCorpsBlue
import com.connorhansen.publiclandmap.ui.theme.BlmGold
import com.connorhansen.publiclandmap.ui.theme.CountyCityLightBlue
import com.connorhansen.publiclandmap.ui.theme.MilitaryRed
import com.connorhansen.publiclandmap.ui.theme.NpsDarkGreen
import com.connorhansen.publiclandmap.ui.theme.OpenAccessMint
import com.connorhansen.publiclandmap.ui.theme.OtherFederalOlive
import com.connorhansen.publiclandmap.ui.theme.OtherPublicGray
import com.connorhansen.publiclandmap.ui.theme.OtherStateGreen
import com.connorhansen.publiclandmap.ui.theme.StateForestLightGreen
import com.connorhansen.publiclandmap.ui.theme.StateParksBlue
import com.connorhansen.publiclandmap.ui.theme.StateWildlifeOrange
import com.connorhansen.publiclandmap.ui.theme.TribalBrown
import com.connorhansen.publiclandmap.ui.theme.UsfsGreen
import com.connorhansen.publiclandmap.ui.theme.UsfwsTeal
import com.connorhansen.publiclandmap.ui.theme.WildernessPurple

object PublicLandColors {

    const val CATEGORY_USFS = "USFS"
    const val CATEGORY_BLM = "BLM"
    const val CATEGORY_NPS = "National Park Service"
    const val CATEGORY_USFWS = "Fish & Wildlife Service"
    const val CATEGORY_CORPS_RECLAMATION = "Army Corps / Reclamation"
    const val CATEGORY_DNR_STATE_TRUST = "DNR / State Trust"
    const val CATEGORY_STATE_PARKS = "State Parks"
    const val CATEGORY_STATE_WILDLIFE = "State Wildlife"
    const val CATEGORY_LOCAL_GOVERNMENT = "Local Government"
    const val CATEGORY_TRIBAL = "Tribal"
    const val CATEGORY_DOE_DEFENSE = "DOE / Defense"
    const val CATEGORY_WILDERNESS_CONSERVATION = "Wilderness / Conservation"
    const val CATEGORY_OPEN_ACCESS = "Open Access"
    const val CATEGORY_OTHER_FEDERAL = "Other Federal"
    const val CATEGORY_OTHER_STATE = "Other State"
    const val CATEGORY_OTHER_PUBLIC = "Other Public"

    data class LegendItem(
        val category: String,
        val color: Color,
        val hex: String
    )

    val legendItems = listOf(
        LegendItem(CATEGORY_USFS, UsfsGreen, "#228B22"),
        LegendItem(CATEGORY_BLM, BlmGold, "#DAA520"),
        LegendItem(CATEGORY_NPS, NpsDarkGreen, "#006400"),
        LegendItem(CATEGORY_USFWS, UsfwsTeal, "#008B8B"),
        LegendItem(CATEGORY_CORPS_RECLAMATION, ArmyCorpsBlue, "#2F80ED"),
        LegendItem(CATEGORY_DNR_STATE_TRUST, StateForestLightGreen, "#90EE90"),
        LegendItem(CATEGORY_STATE_PARKS, StateParksBlue, "#4169E1"),
        LegendItem(CATEGORY_STATE_WILDLIFE, StateWildlifeOrange, "#FF8C00"),
        LegendItem(CATEGORY_LOCAL_GOVERNMENT, CountyCityLightBlue, "#87CEEB"),
        LegendItem(CATEGORY_TRIBAL, TribalBrown, "#8B4513"),
        LegendItem(CATEGORY_DOE_DEFENSE, MilitaryRed, "#DC143C"),
        LegendItem(CATEGORY_WILDERNESS_CONSERVATION, WildernessPurple, "#8B008B"),
        LegendItem(CATEGORY_OPEN_ACCESS, OpenAccessMint, "#2AA876"),
        LegendItem(CATEGORY_OTHER_FEDERAL, OtherFederalOlive, "#6A7D39"),
        LegendItem(CATEGORY_OTHER_STATE, OtherStateGreen, "#66A61E"),
        LegendItem(CATEGORY_OTHER_PUBLIC, OtherPublicGray, "#808080")
    )

    val mapStyleStops: List<Pair<String, String>> = buildList {
        legendItems.forEach { add(it.category to it.hex) }
        add("US Forest Service" to "#228B22")
        add("Forest Service" to "#228B22")
        add("US Bureau of Land Management" to "#DAA520")
        add("Bureau of Land Management" to "#DAA520")
        add("National Park Service" to "#006400")
        add("US Fish and Wildlife Service" to "#008B8B")
        add("U.S. Fish and Wildlife Service" to "#008B8B")
        add("Army Corps of Engineers" to "#2F80ED")
        add("US Army Corps of Engineers" to "#2F80ED")
        add("Bureau of Reclamation" to "#2F80ED")
        add("WA Department of Natural Resources" to "#90EE90")
        add("Washington Department of Natural Resources" to "#90EE90")
        add("State Department of Natural Resources" to "#90EE90")
        add("State Department of Land" to "#90EE90")
        add("State Park and Recreation" to "#4169E1")
        add("WA State Parks and Recreation Commission" to "#4169E1")
        add("State Fish and Wildlife" to "#FF8C00")
        add("WA Dept of Fish and Wildlife" to "#FF8C00")
        add("County" to "#87CEEB")
        add("County Land" to "#87CEEB")
        add("City or Municipality" to "#87CEEB")
        add("City Land" to "#87CEEB")
        add("Tribal Govt" to "#8B4513")
        add("American Indian Lands" to "#8B4513")
        add("US Department of Energy" to "#DC143C")
        add("Department of Energy" to "#DC143C")
        add("US Dept of Defense" to "#DC143C")
        add("Department of Defense" to "#DC143C")
    }.distinctBy { it.first }

    fun getCategory(
        managingAgency: String,
        agencyCategory: String? = null,
        designationType: String? = null,
        publicAccess: String? = null
    ): String {
        agencyCategory?.takeIf { it.isNotBlank() }?.let { return it }

        val text = listOfNotNull(managingAgency, designationType, publicAccess)
            .joinToString(" ")
            .uppercase()
        val access = publicAccess.orEmpty().uppercase()

        return when {
            "BUREAU OF LAND MANAGEMENT" in text || "BLM" in text -> CATEGORY_BLM
            "FOREST SERVICE" in text || "USFS" in text -> CATEGORY_USFS
            "NATIONAL PARK" in text || "NPS" in text -> CATEGORY_NPS
            "FISH AND WILDLIFE" in text || "USFWS" in text || " FWS" in text -> CATEGORY_USFWS
            "ARMY CORPS" in text || "CORPS OF ENGINEERS" in text || "BUREAU OF RECLAMATION" in text -> CATEGORY_CORPS_RECLAMATION
            "DEPARTMENT OF ENERGY" in text || "DEPT OF ENERGY" in text || "DEFENSE" in text || "MILITARY" in text || "AIR FORCE" in text || "NAVY" in text -> CATEGORY_DOE_DEFENSE
            "DNR" in text || "DEPT OF NATURAL" in text || "DEPARTMENT OF NATURAL" in text || "DEPARTMENT OF LAND" in text || "STATE FOREST" in text || "COMMON SCHOOL" in text -> CATEGORY_DNR_STATE_TRUST
            "STATE PARK" in text -> CATEGORY_STATE_PARKS
            "STATE FISH" in text || "STATE WILDLIFE" in text || "WDFW" in text || "ODFW" in text -> CATEGORY_STATE_WILDLIFE
            "COUNTY" in text || "CITY" in text || "MUNICIPAL" in text || "LOCAL" in text || "REGIONAL" in text || "METRO" in text -> CATEGORY_LOCAL_GOVERNMENT
            "TRIBAL" in text || "AMERICAN INDIAN" in text || "RESERVATION" in text || "INDIAN" in text -> CATEGORY_TRIBAL
            "WILDERNESS" in text || "CONSERVATION" in text || "NATURAL AREA" in text || "NRCA" in text || "NAP" in text -> CATEGORY_WILDERNESS_CONSERVATION
            "FEDERAL" in text -> CATEGORY_OTHER_FEDERAL
            "STATE" in text -> CATEGORY_OTHER_STATE
            access == "OPEN" || access == "RESTRICTED" -> CATEGORY_OPEN_ACCESS
            else -> CATEGORY_OTHER_PUBLIC
        }
    }

    fun getColor(
        managingAgency: String,
        agencyCategory: String? = null,
        designationType: String? = null,
        publicAccess: String? = null
    ): Color {
        val category = getCategory(managingAgency, agencyCategory, designationType, publicAccess)
        return legendItems.firstOrNull { it.category == category }?.color ?: OtherPublicGray
    }

    fun getColorHex(
        managingAgency: String,
        agencyCategory: String? = null,
        designationType: String? = null,
        publicAccess: String? = null
    ): String {
        val category = getCategory(managingAgency, agencyCategory, designationType, publicAccess)
        return legendItems.firstOrNull { it.category == category }?.hex ?: "#808080"
    }

    fun getMapLibreColorExpression(): String {
        val stops = mapStyleStops.joinToString(",\n") { (label, color) ->
            "                \"$label\", \"$color\""
        }
        return """
            [
                "match",
                ["coalesce", ["get", "agency_category"], ["get", "managing_agency"]],
$stops,
                "#808080"
            ]
        """.trimIndent()
    }
}
