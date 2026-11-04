package com.connorhansen.publiclandmap.presentation.map

import android.location.Location
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.BuildConfig
import com.connorhansen.publiclandmap.data.local.db.dao.WaypointDao
import com.connorhansen.publiclandmap.data.local.db.entity.WaypointEntity
import com.connorhansen.publiclandmap.data.local.tiles.LocalTileServer
import com.connorhansen.publiclandmap.data.local.tiles.OfflineTileProvider
import com.connorhansen.publiclandmap.data.repository.ParcelRepository
import com.connorhansen.publiclandmap.data.repository.PublicLandRepository
import com.connorhansen.publiclandmap.data.repository.RegulationRepository
import com.connorhansen.publiclandmap.domain.model.Parcel
import com.connorhansen.publiclandmap.domain.model.PublicLand
import com.connorhansen.publiclandmap.domain.model.Regulation
import com.connorhansen.publiclandmap.util.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject

sealed class SearchResult {
    data class PublicLandResult(val publicLand: PublicLand) : SearchResult()
    data class ParcelResult(val parcel: Parcel) : SearchResult()
    data class PlaceResult(
        val name: String,
        val lat: Double,
        val lng: Double,
        val displayName: String
    ) : SearchResult()
}

data class FlyToTarget(
    val lat: Double,
    val lng: Double,
    val zoom: Double = 14.0
)

@OptIn(FlowPreview::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val parcelRepository: ParcelRepository,
    private val publicLandRepository: PublicLandRepository,
    private val regulationRepository: RegulationRepository,
    private val waypointDao: WaypointDao,
    private val okHttpClient: OkHttpClient,
    private val connectivityObserver: ConnectivityObserver,
    private val offlineTileProvider: OfflineTileProvider,
    private val localTileServer: LocalTileServer
) : ViewModel() {

    companion object {
        private const val TAG = "MapViewModel"
        val TILE_BASE_URL: String = BuildConfig.TILE_BASE_URL
    }

    enum class Basemap(val title: String, val url: String) {
        TOPO(
            "Topographic",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSTopo/MapServer/tile/{z}/{y}/{x}"
        ),
        SATELLITE(
            "Satellite",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"
        ),
        HYBRID(
            "Hybrid",
            "https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/{z}/{y}/{x}"
        ),
        OSM(
            "OpenStreetMap",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        )
    }

    enum class MapLayer(val title: String, val category: LayerCategory) {
        PUBLIC_LANDS("Public Lands", LayerCategory.LAND_OVERLAYS),
        PARCELS("Property Boundaries", LayerCategory.LAND_OVERLAYS),
        PLSS_GRID("PLSS Grid", LayerCategory.LAND_OVERLAYS),
        USFS_ROADS("USFS Roads", LayerCategory.ACCESS_RECREATION),
        TRAILS("Trails", LayerCategory.ACCESS_RECREATION),
        CAMPGROUNDS("Campgrounds", LayerCategory.ACCESS_RECREATION),
        TRAILHEADS("Trailheads", LayerCategory.ACCESS_RECREATION),
        FIRE_RESTRICTIONS("Fire Restrictions", LayerCategory.REGULATIONS),
        AREA_CLOSURES("Area Closures", LayerCategory.REGULATIONS),
        OHV_DESIGNATIONS("OHV Designations", LayerCategory.REGULATIONS)
    }

    enum class LayerCategory(val title: String) {
        LAND_OVERLAYS("Land Overlays"),
        ACCESS_RECREATION("Access & Recreation"),
        REGULATIONS("Regulations")
    }

    sealed class SelectedFeature {
        data class PublicLand(
            val id: String,
            val properties: Map<String, String>
        ) : SelectedFeature()

        data class Parcel(
            val id: String,
            val properties: Map<String, String>
        ) : SelectedFeature()

        data class RecreationSite(
            val id: String,
            val properties: Map<String, String>
        ) : SelectedFeature()
    }

    data class MapState(
        // Starts over the Mount Rainier area; the camera moves as soon as the user pans or searches
        val cameraLat: Double = 46.85,
        val cameraLng: Double = -121.76,
        val cameraZoom: Double = 10.0,
        val cameraBearing: Double = 0.0,
        val cameraTilt: Double = 0.0,
        val selectedBasemap: Basemap = Basemap.TOPO,
        val visibleLayers: Set<MapLayer> = setOf(MapLayer.PUBLIC_LANDS, MapLayer.PARCELS),
        val userLocation: Location? = null,
        val isFollowingUser: Boolean = false,
        val selectedFeature: SelectedFeature? = null,
        val isLayerPanelVisible: Boolean = false,
        val showSearchBar: Boolean = true,
        val locationPermissionGranted: Boolean = false,
        val shouldRecenter: Boolean = false,
        val shouldResetBearing: Boolean = false,
        val styleRevision: Int = 0,
        // Search state
        val searchQuery: String = "",
        val searchResults: List<SearchResult> = emptyList(),
        val isSearching: Boolean = false,
        // Feature detail state
        val selectedPublicLand: PublicLand? = null,
        val selectedParcel: Parcel? = null,
        val selectedRegulations: List<Regulation> = emptyList(),
        val isLoadingFeatureDetails: Boolean = false,
        // Fly-to
        val flyToTarget: FlyToTarget? = null,
        // Offline
        val isOnline: Boolean = true,
        val hasOfflineTiles: Boolean = false,
        val localTileBaseUrl: String? = null
    )

    private val _state = MutableStateFlow(MapState())
    val state: StateFlow<MapState> = _state.asStateFlow()

    private val _searchQueryFlow = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        // Start local tile server if offline tiles exist
        if (offlineTileProvider.hasOfflineTiles()) {
            localTileServer.startServer()
            _state.update {
                it.copy(
                    hasOfflineTiles = true,
                    localTileBaseUrl = localTileServer.baseUrl
                )
            }
        }

        // Observe connectivity
        viewModelScope.launch {
            connectivityObserver.observe().collect { online ->
                val hasLocal = offlineTileProvider.hasOfflineTiles()
                if (hasLocal && !localTileServer.isRunning) {
                    localTileServer.startServer()
                }
                val current = _state.value
                val needsStyleRebuild = current.isOnline != online || current.hasOfflineTiles != hasLocal
                _state.update {
                    it.copy(
                        isOnline = online,
                        hasOfflineTiles = hasLocal,
                        localTileBaseUrl = if (hasLocal) localTileServer.baseUrl else null,
                        styleRevision = if (needsStyleRebuild) it.styleRevision + 1 else it.styleRevision
                    )
                }
            }
        }

        viewModelScope.launch {
            _searchQueryFlow
                .debounce(300L)
                .distinctUntilChanged()
                .filter { it.length >= 2 }
                .collect { query ->
                    performSearch(query)
                }
        }
    }

    fun onSearchQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.length < 2) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
        }
        _searchQueryFlow.value = query
    }

    fun clearSearch() {
        _state.update {
            it.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
        _searchQueryFlow.value = ""
    }

    fun onSearchResultSelected(result: SearchResult) {
        clearSearch()
        when (result) {
            is SearchResult.PublicLandResult -> {
                val pl = result.publicLand
                val lat = pl.centroidLat ?: return
                val lng = pl.centroidLng ?: return
                _state.update {
                    it.copy(
                        selectedFeature = SelectedFeature.PublicLand(
                            id = pl.id,
                            properties = buildPublicLandProps(pl)
                        ),
                        selectedPublicLand = pl,
                        selectedRegulations = emptyList(),
                        flyToTarget = FlyToTarget(lat, lng, 12.0)
                    )
                }
                loadRegulationsForPublicLand(pl.id)
            }
            is SearchResult.ParcelResult -> {
                val p = result.parcel
                _state.update {
                    it.copy(
                        selectedFeature = SelectedFeature.Parcel(
                            id = p.id,
                            properties = buildParcelProps(p)
                        ),
                        selectedParcel = p,
                        flyToTarget = FlyToTarget(p.centroidLat, p.centroidLng, 16.0)
                    )
                }
            }
            is SearchResult.PlaceResult -> {
                _state.update {
                    it.copy(
                        flyToTarget = FlyToTarget(result.lat, result.lng, 14.0)
                    )
                }
            }
        }
    }

    fun onFlyToConsumed() {
        _state.update { it.copy(flyToTarget = null) }
    }

    private fun performSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isSearching = true) }
            val results = mutableListOf<SearchResult>()

            // Each source can fail on its own (no network, backend down); one failure must not
            // hide results from the others. The error is logged, and cancellation is rethrown.
            results.addAll(
                searchSource("public lands") {
                    publicLandRepository.searchPublicLands(query).take(5).map { SearchResult.PublicLandResult(it) }
                }
            )
            results.addAll(
                searchSource("parcels") {
                    parcelRepository.searchParcels(query).take(5).map { SearchResult.ParcelResult(it) }
                }
            )
            results.addAll(
                searchSource("places") {
                    geocodeNominatim(query).take(5)
                }
            )

            _state.update {
                it.copy(
                    searchResults = results,
                    isSearching = false
                )
            }
        }
    }

    private suspend fun <T> searchSource(name: String, block: suspend () -> List<T>): List<T> {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Search of $name failed", e)
            emptyList()
        }
    }

    private suspend fun geocodeNominatim(query: String): List<SearchResult.PlaceResult> {
        val encoded = Uri.encode(query)
        val url = "https://nominatim.openstreetmap.org/search?format=json&countrycodes=us&limit=5&q=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PublicLandMap/1.0")
            .get()
            .build()

        return withContext(Dispatchers.IO) {
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val jsonArray = JSONArray(body)
            val results = mutableListOf<SearchResult.PlaceResult>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val displayName = obj.optString("display_name", "")
                val lat = obj.optString("lat", "").toDoubleOrNull() ?: continue
                val lng = obj.optString("lon", "").toDoubleOrNull() ?: continue
                val name = displayName.split(",").firstOrNull()?.trim() ?: displayName
                results.add(SearchResult.PlaceResult(name, lat, lng, displayName))
            }

            results
        }
    }

    fun setBasemap(basemap: Basemap) {
        _state.update {
            it.copy(
                selectedBasemap = basemap,
                styleRevision = it.styleRevision + 1
            )
        }
    }

    fun toggleLayer(layer: MapLayer) {
        _state.update { current ->
            val newLayers = if (layer in current.visibleLayers) {
                current.visibleLayers - layer
            } else {
                current.visibleLayers + layer
            }
            current.copy(
                visibleLayers = newLayers,
                styleRevision = current.styleRevision + 1
            )
        }
    }

    fun isLayerVisible(layer: MapLayer): Boolean {
        return layer in _state.value.visibleLayers
    }

    fun updateUserLocation(location: Location) {
        _state.update { it.copy(userLocation = location) }
    }

    fun setLocationPermissionGranted(granted: Boolean) {
        _state.update { it.copy(locationPermissionGranted = granted) }
    }

    fun onMapClick(layerId: String?, featureId: String?, properties: Map<String, String>) {
        if (layerId == null || featureId == null) {
            _state.update {
                it.copy(
                    selectedFeature = null,
                    selectedPublicLand = null,
                    selectedParcel = null,
                    selectedRegulations = emptyList()
                )
            }
            return
        }
        val feature = when {
            layerId.startsWith("public-lands") -> SelectedFeature.PublicLand(featureId, properties)
            layerId.startsWith("parcels") -> SelectedFeature.Parcel(featureId, properties)
            layerId.startsWith("recreation") || layerId.startsWith("campground") || layerId.startsWith("trailhead") ->
                SelectedFeature.RecreationSite(featureId, properties)
            else -> null
        }
        _state.update {
            it.copy(
                selectedFeature = feature,
                selectedPublicLand = null,
                selectedParcel = null,
                selectedRegulations = emptyList(),
                isLoadingFeatureDetails = feature != null
            )
        }

        // Load full details from repository
        when (feature) {
            is SelectedFeature.PublicLand -> loadPublicLandDetails(feature.id)
            is SelectedFeature.Parcel -> loadParcelDetails(feature.id)
            is SelectedFeature.RecreationSite -> {
                _state.update { it.copy(isLoadingFeatureDetails = false) }
            }
            null -> {}
        }
    }

    private fun loadPublicLandDetails(id: String) {
        viewModelScope.launch {
            try {
                val publicLand = publicLandRepository.getPublicLandById(id)
                _state.update {
                    it.copy(
                        selectedPublicLand = publicLand,
                        isLoadingFeatureDetails = false
                    )
                }
                if (publicLand != null) {
                    loadRegulationsForPublicLand(id)
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingFeatureDetails = false) }
            }
        }
    }

    private fun loadRegulationsForPublicLand(areaId: String) {
        viewModelScope.launch {
            try {
                val regulations = regulationRepository.getRegulationsForArea(areaId)
                _state.update { it.copy(selectedRegulations = regulations) }
            } catch (_: Exception) {
                // Regulations are non-critical, keep empty list
            }
        }
    }

    private fun loadParcelDetails(id: String) {
        viewModelScope.launch {
            try {
                val parcel = parcelRepository.getParcelById(id)
                _state.update {
                    it.copy(
                        selectedParcel = parcel,
                        isLoadingFeatureDetails = false
                    )
                }
            } catch (_: Exception) {
                _state.update { it.copy(isLoadingFeatureDetails = false) }
            }
        }
    }

    fun clearSelectedFeature() {
        _state.update {
            it.copy(
                selectedFeature = null,
                selectedPublicLand = null,
                selectedParcel = null,
                selectedRegulations = emptyList(),
                isLoadingFeatureDetails = false
            )
        }
    }

    fun saveWaypointFromFeature(): Boolean {
        val feature = _state.value.selectedFeature ?: return false
        val name: String
        val lat: Double
        val lng: Double

        when (feature) {
            is SelectedFeature.PublicLand -> {
                val pl = _state.value.selectedPublicLand
                name = pl?.unitName ?: feature.properties["unit_name"] ?: "Public Land"
                lat = pl?.centroidLat ?: feature.properties["centroid_lat"]?.toDoubleOrNull() ?: return false
                lng = pl?.centroidLng ?: feature.properties["centroid_lng"]?.toDoubleOrNull() ?: return false
            }
            is SelectedFeature.Parcel -> {
                val p = _state.value.selectedParcel
                name = p?.parcelNumber ?: feature.properties["parcel_number"] ?: "Parcel"
                lat = p?.centroidLat ?: feature.properties["centroid_lat"]?.toDoubleOrNull() ?: return false
                lng = p?.centroidLng ?: feature.properties["centroid_lng"]?.toDoubleOrNull() ?: return false
            }
            is SelectedFeature.RecreationSite -> {
                name = feature.properties["name"] ?: "Recreation Site"
                lat = feature.properties["latitude"]?.toDoubleOrNull() ?: return false
                lng = feature.properties["longitude"]?.toDoubleOrNull() ?: return false
            }
        }

        val now = System.currentTimeMillis()
        val entity = WaypointEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            description = null,
            latitude = lat,
            longitude = lng,
            elevation = null,
            icon = "pin",
            color = "#FF0000",
            createdAt = now,
            updatedAt = now
        )
        viewModelScope.launch {
            waypointDao.insert(entity)
        }
        return true
    }

    fun getDirectionsUri(): Uri? {
        val feature = _state.value.selectedFeature ?: return null
        val lat: Double
        val lng: Double

        when (feature) {
            is SelectedFeature.PublicLand -> {
                val pl = _state.value.selectedPublicLand
                lat = pl?.centroidLat ?: feature.properties["centroid_lat"]?.toDoubleOrNull() ?: return null
                lng = pl?.centroidLng ?: feature.properties["centroid_lng"]?.toDoubleOrNull() ?: return null
            }
            is SelectedFeature.Parcel -> {
                val p = _state.value.selectedParcel
                lat = p?.centroidLat ?: feature.properties["centroid_lat"]?.toDoubleOrNull() ?: return null
                lng = p?.centroidLng ?: feature.properties["centroid_lng"]?.toDoubleOrNull() ?: return null
            }
            is SelectedFeature.RecreationSite -> {
                lat = feature.properties["latitude"]?.toDoubleOrNull() ?: return null
                lng = feature.properties["longitude"]?.toDoubleOrNull() ?: return null
            }
        }
        return Uri.parse("google.navigation:q=$lat,$lng")
    }

    fun toggleLayerPanel() {
        _state.update { it.copy(isLayerPanelVisible = !it.isLayerPanelVisible) }
    }

    fun hideLayerPanel() {
        _state.update { it.copy(isLayerPanelVisible = false) }
    }

    fun centerOnUser() {
        val loc = _state.value.userLocation
        if (loc != null) {
            _state.update {
                it.copy(
                    cameraLat = loc.latitude,
                    cameraLng = loc.longitude,
                    cameraZoom = maxOf(it.cameraZoom, 14.0),
                    isFollowingUser = true,
                    shouldRecenter = true
                )
            }
        }
    }

    fun onRecenterConsumed() {
        _state.update { it.copy(shouldRecenter = false) }
    }

    fun resetBearing() {
        _state.update {
            it.copy(
                cameraBearing = 0.0,
                shouldResetBearing = true
            )
        }
    }

    fun onResetBearingConsumed() {
        _state.update { it.copy(shouldResetBearing = false) }
    }

    fun updateCamera(lat: Double, lng: Double, zoom: Double, bearing: Double, tilt: Double) {
        _state.update {
            it.copy(
                cameraLat = lat,
                cameraLng = lng,
                cameraZoom = zoom,
                cameraBearing = bearing,
                cameraTilt = tilt,
                isFollowingUser = false
            )
        }
    }

    fun getTileSourceUrl(sourceName: String): String {
        return "$TILE_BASE_URL/tiles/$sourceName/{z}/{x}/{y}.pbf"
    }

    fun getEffectiveTileBaseUrl(): String {
        val s = _state.value
        return if (!s.isOnline && s.hasOfflineTiles && s.localTileBaseUrl != null) {
            s.localTileBaseUrl
        } else {
            TILE_BASE_URL
        }
    }

    override fun onCleared() {
        super.onCleared()
        localTileServer.stopServer()
        offlineTileProvider.closeAll()
    }

    private fun buildPublicLandProps(pl: PublicLand): Map<String, String> {
        return buildMap {
            put("unit_name", pl.unitName)
            put("managing_agency", pl.managingAgency)
            pl.agencyCategory?.let { put("agency_category", it) }
            put("designation_type", pl.designationType)
            pl.publicAccess?.let { put("public_access", it) }
            put("state", pl.state)
            pl.gisAcres?.let { put("gis_acres", it.toString()) }
        }
    }

    private fun buildParcelProps(p: Parcel): Map<String, String> {
        return buildMap {
            put("parcel_number", p.parcelNumber)
            p.ownerName?.let { put("owner_name", it) }
            put("county", p.county)
            put("state", p.state)
            p.acreage?.let { put("acreage", it.toString()) }
            p.landUseDesc?.let { put("land_use", it) }
        }
    }
}
