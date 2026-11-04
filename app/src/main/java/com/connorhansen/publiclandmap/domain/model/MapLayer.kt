package com.connorhansen.publiclandmap.domain.model

enum class MapLayer(val title: String, val category: LayerCategory) {
    PUBLIC_LANDS("Public Lands", LayerCategory.LAND_OVERLAYS),
    PARCELS("Property Boundaries", LayerCategory.LAND_OVERLAYS),
    PLSS_GRID("PLSS Grid", LayerCategory.LAND_OVERLAYS),
    ROADS("USFS Roads", LayerCategory.ACCESS_RECREATION),
    TRAILS("Trails", LayerCategory.ACCESS_RECREATION),
    CAMPGROUNDS("Campgrounds", LayerCategory.ACCESS_RECREATION),
    TRAILHEADS("Trailheads", LayerCategory.ACCESS_RECREATION),
    FIRE_RESTRICTIONS("Fire Restrictions", LayerCategory.REGULATIONS),
    CLOSURES("Area Closures", LayerCategory.REGULATIONS),
    OHV("OHV Designations", LayerCategory.REGULATIONS)
}

enum class LayerCategory(val title: String) {
    LAND_OVERLAYS("Land Overlays"),
    ACCESS_RECREATION("Access & Recreation"),
    REGULATIONS("Regulations")
}
