package com.connorhansen.publiclandmap.presentation.map

import android.graphics.PointF
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.connorhansen.publiclandmap.util.PublicLandColors
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@Composable
fun MapLibreView(
    state: MapViewModel.MapState,
    onCameraChanged: (lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double) -> Unit,
    onMapClick: (layerId: String?, featureId: String?, properties: Map<String, String>) -> Unit,
    onRecenterConsumed: () -> Unit,
    onResetBearingConsumed: () -> Unit,
    onFlyToConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var lastStyleRevision by remember { mutableIntStateOf(-1) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }

    // Initialize MapLibre once
    remember {
        MapLibre.getInstance(context)
        true
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { mapView ->
                mapView.onCreate(Bundle())
                mapViewRef = mapView

                mapView.getMapAsync { mapLibreMap ->
                    mapRef = mapLibreMap

                    mapLibreMap.uiSettings.isCompassEnabled = false
                    mapLibreMap.uiSettings.isLogoEnabled = false
                    mapLibreMap.uiSettings.isAttributionEnabled = false

                    val initialCamera = CameraPosition.Builder()
                        .target(LatLng(state.cameraLat, state.cameraLng))
                        .zoom(state.cameraZoom)
                        .bearing(state.cameraBearing)
                        .tilt(state.cameraTilt)
                        .build()
                    mapLibreMap.cameraPosition = initialCamera

                    val styleJson = buildStyleJson(state)
                    lastStyleRevision = state.styleRevision
                    mapLibreMap.setStyle(Style.Builder().fromJson(styleJson))

                    mapLibreMap.addOnMapClickListener { latLng ->
                        val screenPoint = mapLibreMap.projection.toScreenLocation(latLng)
                        handleMapClick(mapLibreMap, screenPoint, onMapClick)
                        true
                    }

                    mapLibreMap.addOnCameraMoveListener {
                        val pos = mapLibreMap.cameraPosition
                        val target = pos.target
                        if (target != null) {
                            onCameraChanged(
                                target.latitude,
                                target.longitude,
                                pos.zoom,
                                pos.bearing,
                                pos.tilt
                            )
                        }
                    }
                }
            }
        },
        update = { _ ->
            // Style changes handled via LaunchedEffect below
        }
    )

    // React to style revision changes (basemap or layer visibility)
    LaunchedEffect(state.styleRevision) {
        val map = mapRef ?: return@LaunchedEffect
        if (state.styleRevision != lastStyleRevision) {
            lastStyleRevision = state.styleRevision
            val styleJson = buildStyleJson(state)
            map.setStyle(Style.Builder().fromJson(styleJson))
        }
    }

    // Handle recenter
    LaunchedEffect(state.shouldRecenter) {
        if (state.shouldRecenter) {
            val map = mapRef ?: return@LaunchedEffect
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(state.cameraLat, state.cameraLng))
                    .zoom(state.cameraZoom)
                    .build()
            )
            map.animateCamera(cameraUpdate, 600)
            onRecenterConsumed()
        }
    }

    // Handle reset bearing
    LaunchedEffect(state.shouldResetBearing) {
        if (state.shouldResetBearing) {
            val map = mapRef ?: return@LaunchedEffect
            val currentPos = map.cameraPosition
            val cameraUpdate = CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(currentPos.target)
                    .zoom(currentPos.zoom)
                    .bearing(0.0)
                    .tilt(currentPos.tilt)
                    .build()
            )
            map.animateCamera(cameraUpdate, 400)
            onResetBearingConsumed()
        }
    }

    // Handle fly-to from search results
    LaunchedEffect(state.flyToTarget) {
        val target = state.flyToTarget ?: return@LaunchedEffect
        val map = mapRef ?: return@LaunchedEffect
        val cameraUpdate = CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(target.lat, target.lng))
                .zoom(target.zoom)
                .build()
        )
        map.animateCamera(cameraUpdate, 800)
        onFlyToConsumed()
    }

    // Lifecycle management
    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                Lifecycle.Event.ON_DESTROY -> mv.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapViewRef?.onDestroy()
        }
    }
}

private val PUBLIC_LAND_CLICKABLE_LAYER_IDS = listOf(
    "public-lands-fill",
    "public-lands-line"
)

private val PARCEL_CLICKABLE_LAYER_IDS = listOf(
    "parcels-fill",
    "parcels-line"
)

private fun handleMapClick(
    map: MapLibreMap,
    screenPoint: PointF,
    onMapClick: (String?, String?, Map<String, String>) -> Unit
) {
    val publicLandRect = android.graphics.RectF(
        screenPoint.x - 24f,
        screenPoint.y - 24f,
        screenPoint.x + 24f,
        screenPoint.y + 24f
    )
    val parcelRect = android.graphics.RectF(
        screenPoint.x - 10f,
        screenPoint.y - 10f,
        screenPoint.x + 10f,
        screenPoint.y + 10f
    )
    val selectedLayerAndFeature = findRenderedFeature(map, publicLandRect, PUBLIC_LAND_CLICKABLE_LAYER_IDS)
        ?: findRenderedFeature(map, parcelRect, PARCEL_CLICKABLE_LAYER_IDS)

    if (selectedLayerAndFeature != null) {
        val (layerId, feature) = selectedLayerAndFeature
        val props = mutableMapOf<String, String>()
        feature.properties().let { jsonObj ->
            jsonObj.entrySet().forEach { entry ->
                val value = entry.value
                if (!value.isJsonNull) {
                    props[entry.key] = if (value.isJsonPrimitive) {
                        value.asString
                    } else {
                        value.toString()
                    }
                }
            }
        }
        val featureId = props["id"] ?: feature.id() ?: props["gid"] ?: "unknown"
        onMapClick(layerId, featureId, props)
    } else {
        onMapClick(null, null, emptyMap())
    }
}

private fun findRenderedFeature(
    map: MapLibreMap,
    rect: android.graphics.RectF,
    layerIds: List<String>
) = layerIds.firstNotNullOfOrNull { layerId ->
    map.queryRenderedFeatures(rect, layerId).firstOrNull()?.let { feature -> layerId to feature }
}

private fun buildStyleJson(state: MapViewModel.MapState): String {
    val root = JSONObject()
    root.put("version", 8)
    root.put("name", "Public Land Map")

    val sources = JSONObject()
    val layers = JSONArray()

    // Basemap raster source
    val basemapSource = JSONObject().apply {
        put("type", "raster")
        put("tiles", JSONArray().put(state.selectedBasemap.url))
        put("tileSize", 256)
        put("attribution", "")
    }
    sources.put("basemap", basemapSource)

    val basemapLayer = JSONObject().apply {
        put("id", "basemap-raster")
        put("type", "raster")
        put("source", "basemap")
        put("minzoom", 0)
        put("maxzoom", 22)
    }
    layers.put(basemapLayer)

    // For Hybrid mode, add topo overlay on top of satellite
    if (state.selectedBasemap == MapViewModel.Basemap.HYBRID) {
        val topoOverlaySource = JSONObject().apply {
            put("type", "raster")
            put("tiles", JSONArray().put("https://tile.opentopomap.org/{z}/{x}/{y}.png"))
            put("tileSize", 256)
        }
        sources.put("topo-overlay", topoOverlaySource)

        val topoOverlayLayer = JSONObject().apply {
            put("id", "topo-overlay-raster")
            put("type", "raster")
            put("source", "topo-overlay")
            put("paint", JSONObject().apply {
                put("raster-opacity", 0.4)
            })
        }
        layers.put(topoOverlayLayer)
    }

    val tileBase = if (!state.isOnline && state.hasOfflineTiles && state.localTileBaseUrl != null) {
        state.localTileBaseUrl
    } else {
        MapViewModel.TILE_BASE_URL
    }

    // Public Lands - live from PostGIS via Martin (WA/OR data)
    addVectorSource(sources, "public-lands", "$tileBase/public_lands/{z}/{x}/{y}", minZoom = 4, maxZoom = 15)

    // Parcels - live from PostGIS via Martin (has correct column names)
    addVectorSource(sources, "parcels", "$tileBase/parcels/{z}/{x}/{y}", minZoom = 12, maxZoom = 15)
    if (MapViewModel.MapLayer.PARCELS in state.visibleLayers) {
        // Transparent fill layer for click detection anywhere inside parcel
        val fillPaint = JSONObject().apply {
            put("fill-color", "#000000")
            put("fill-opacity", 0.01)
        }
        layers.put(createLayerJson("parcels-fill", "fill", "parcels", "parcels", fillPaint, minZoom = 12))

        // Visible boundary lines
        val linePaint = JSONObject().apply {
            put("line-color", "#888888")
            put("line-width", JSONArray().apply {
                put("interpolate")
                put(JSONArray().put("linear"))
                put(JSONArray().put("zoom"))
                put(12); put(0.5)
                put(14); put(1.0)
            })
        }
        layers.put(createLayerJson("parcels-line", "line", "parcels", "parcels", linePaint, minZoom = 12))
    }

    if (MapViewModel.MapLayer.PUBLIC_LANDS in state.visibleLayers) {
        val agencyColorExpr = publicLandAgencyColorExpression()
        val publicLandFillPaint = JSONObject().apply {
            put("fill-color", agencyColorExpr)
            put("fill-opacity", 0.35)
        }
        layers.put(createLayerJson("public-lands-fill", "fill", "public-lands", "public_lands", publicLandFillPaint, minZoom = 4))

        val publicLandLinePaint = JSONObject().apply {
            put("line-color", agencyColorExpr)
            put("line-width", 1.5)
            put("line-opacity", 0.8)
        }
        layers.put(createLayerJson("public-lands-line", "line", "public-lands", "public_lands", publicLandLinePaint, minZoom = 4))
    }

    // Parcels - OR - disabled (no data loaded)

    // PLSS Grid - disabled (no data loaded)
    // addVectorSource(sources, "plss", "$tileBase/tiles/plss/{z}/{x}/{y}.pbf")
    // PLSS layers disabled — source not available

    // Roads and Trails - disabled (no data loaded)
    // addVectorSource(sources, "roads-trails", "$tileBase/tiles/roads-trails/{z}/{x}/{y}.pbf")
    // Road and trail layers disabled — source not available

    // Recreation POIs - disabled (no data loaded)
    // addVectorSource(sources, "recreation-pois", "$tileBase/tiles/recreation-pois/{z}/{x}/{y}.pbf")
    // Recreation POI layers disabled — source not available

    root.put("sources", sources)
    root.put("layers", layers)

    return root.toString()
}

private fun publicLandAgencyColorExpression(): JSONArray {
    return JSONArray().apply {
        put("match")
        put(JSONArray().apply {
            put("coalesce")
            put(JSONArray().apply { put("get"); put("agency_category") })
            put(JSONArray().apply { put("get"); put("managing_agency") })
        })
        PublicLandColors.mapStyleStops.forEach { (label, color) ->
            put(label)
            put(color)
        }
        put("#808080")
    }
}

private fun addVectorSource(
    sources: JSONObject,
    id: String,
    tileUrl: String,
    minZoom: Int? = null,
    maxZoom: Int? = null
) {
    val source = JSONObject().apply {
        put("type", "vector")
        put("tiles", JSONArray().put(tileUrl))
        if (minZoom != null) put("minzoom", minZoom)
        if (maxZoom != null) put("maxzoom", maxZoom)
    }
    sources.put(id, source)
}

private fun createLayerJson(
    id: String,
    type: String,
    source: String,
    sourceLayer: String,
    paint: JSONObject,
    layout: JSONObject? = null,
    filter: JSONArray? = null,
    minZoom: Int? = null,
    maxZoom: Int? = null
): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("type", type)
        put("source", source)
        put("source-layer", sourceLayer)
        put("paint", paint)
        if (layout != null) put("layout", layout)
        if (filter != null) put("filter", filter)
        if (minZoom != null) put("minzoom", minZoom)
        if (maxZoom != null) put("maxzoom", maxZoom)
    }
}
