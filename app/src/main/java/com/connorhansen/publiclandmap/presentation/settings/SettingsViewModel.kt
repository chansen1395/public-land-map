package com.connorhansen.publiclandmap.presentation.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.data.local.db.AppDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val database: AppDatabase
) : ViewModel() {

    companion object {
        val KEY_USE_IMPERIAL = booleanPreferencesKey("use_imperial")
        val KEY_COORD_FORMAT = intPreferencesKey("coordinate_format")
        val KEY_DEFAULT_BASEMAP = intPreferencesKey("default_basemap")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    data class SettingsState(
        val useImperial: Boolean = true,
        val coordinateFormat: Int = 0, // 0=DD, 1=DM, 2=DMS
        val defaultBasemap: Int = 0,   // 0=Topo, 1=Satellite, 2=Hybrid, 3=OSM
        val isClearingCache: Boolean = false,
        val cacheCleared: Boolean = false
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadPreferences()
    }

    private fun loadPreferences() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _state.update {
                it.copy(
                    useImperial = prefs[KEY_USE_IMPERIAL] ?: true,
                    coordinateFormat = prefs[KEY_COORD_FORMAT] ?: 0,
                    defaultBasemap = prefs[KEY_DEFAULT_BASEMAP] ?: 0
                )
            }
        }
    }

    fun setUseImperial(imperial: Boolean) {
        _state.update { it.copy(useImperial = imperial) }
        viewModelScope.launch {
            dataStore.edit { it[KEY_USE_IMPERIAL] = imperial }
        }
    }

    fun setCoordinateFormat(format: Int) {
        _state.update { it.copy(coordinateFormat = format) }
        viewModelScope.launch {
            dataStore.edit { it[KEY_COORD_FORMAT] = format }
        }
    }

    fun setDefaultBasemap(basemap: Int) {
        _state.update { it.copy(defaultBasemap = basemap) }
        viewModelScope.launch {
            dataStore.edit { it[KEY_DEFAULT_BASEMAP] = basemap }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            _state.update { it.copy(isClearingCache = true, cacheCleared = false) }
            try {
                database.parcelDao().deleteAll()
                database.publicLandDao().deleteAll()
                database.regulationDao().deleteAll()
                _state.update { it.copy(isClearingCache = false, cacheCleared = true) }
            } catch (_: Exception) {
                _state.update { it.copy(isClearingCache = false) }
            }
        }
    }

    fun dismissCacheCleared() {
        _state.update { it.copy(cacheCleared = false) }
    }
}
