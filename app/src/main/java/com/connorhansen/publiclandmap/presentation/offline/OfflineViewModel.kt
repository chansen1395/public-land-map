package com.connorhansen.publiclandmap.presentation.offline

import android.app.Application
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.connorhansen.publiclandmap.data.remote.api.MetadataApi
import com.connorhansen.publiclandmap.data.repository.OfflineRepository
import com.connorhansen.publiclandmap.domain.model.OfflineRegion
import com.connorhansen.publiclandmap.service.DownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class OfflineViewModel @Inject constructor(
    application: Application,
    private val offlineRepository: OfflineRepository,
    private val metadataApi: MetadataApi
) : AndroidViewModel(application) {

    data class UiState(
        val availableRegions: List<OfflineRegion> = emptyList(),
        val downloadedRegions: List<OfflineRegion> = emptyList(),
        val downloadProgress: Map<String, Float> = emptyMap(),
        val activeDownloads: Set<String> = emptySet(),
        val totalStorageUsed: Long = 0L,
        val deviceFreeSpace: Long = Long.MAX_VALUE,
        val updateAvailable: Boolean = false,
        val serverLastUpdated: Long = 0L,
        val isLoading: Boolean = false,
        val isCheckingUpdates: Boolean = false,
        val error: String? = null,
        val selectedTab: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val combinedState: StateFlow<UiState> = combine(
        _uiState,
        offlineRepository.getAllRegions(),
        DownloadService.downloadProgress,
        DownloadService.activeDownloads
    ) { state, downloaded, progress, active ->
        val totalSize = downloaded.filter { it.status == "complete" }
            .sumOf { it.sizeBytes ?: 0L }
        state.copy(
            downloadedRegions = downloaded,
            downloadProgress = progress,
            activeDownloads = active,
            totalStorageUsed = totalSize
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState()
    )

    init {
        loadAvailableRegions()
        checkDeviceFreeSpace()
    }

    fun loadAvailableRegions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val regions = offlineRepository.getAvailableRegions()
                _uiState.update { it.copy(availableRegions = regions, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load available regions. Check your connection."
                    )
                }
            }
        }
    }

    fun startDownload(region: OfflineRegion) {
        viewModelScope.launch {
            try {
                val existing = offlineRepository.getRegionById(region.id)
                if (existing == null) {
                    offlineRepository.saveRegion(region.copy(status = "pending"))
                } else {
                    offlineRepository.updateStatus(region.id, "pending")
                }

                val context = getApplication<Application>()
                DownloadService.startDownload(context, region.id, region.name)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to start download: ${e.message}") }
            }
        }
    }

    fun cancelDownload(regionId: String) {
        val context = getApplication<Application>()
        DownloadService.cancelDownload(context, regionId)
    }

    fun deleteRegion(regionId: String) {
        viewModelScope.launch {
            try {
                offlineRepository.deleteRegion(regionId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete region: ${e.message}") }
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdates = true) }
            try {
                val metadata = metadataApi.getLastUpdated()
                val serverTs = metadata.lastUpdated?.toLongOrNull() ?: 0L

                val downloaded = offlineRepository.getCompletedRegions()
                val oldestDownload = downloaded.minOfOrNull { it.downloadedAt ?: Long.MAX_VALUE }
                    ?: Long.MAX_VALUE

                val hasUpdate = serverTs > oldestDownload && downloaded.isNotEmpty()
                _uiState.update {
                    it.copy(
                        updateAvailable = hasUpdate,
                        serverLastUpdated = serverTs,
                        isCheckingUpdates = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCheckingUpdates = false,
                        error = "Failed to check for updates"
                    )
                }
            }
        }
    }

    fun redownloadRegion(region: OfflineRegion) {
        viewModelScope.launch {
            offlineRepository.deleteRegion(region.id)
            offlineRepository.saveRegion(region.copy(status = "pending", downloadedAt = null, sizeBytes = null))
            val context = getApplication<Application>()
            DownloadService.startDownload(context, region.id, region.name)
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun checkDeviceFreeSpace() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            _uiState.update { it.copy(deviceFreeSpace = freeBytes) }
        } catch (_: Exception) {
            // Not critical
        }
    }

    fun isRegionDownloaded(regionId: String): Boolean {
        return combinedState.value.downloadedRegions.any {
            it.id == regionId && it.status == "complete"
        }
    }

    fun isRegionDownloading(regionId: String): Boolean {
        return combinedState.value.activeDownloads.contains(regionId)
    }

    fun getRegionStatus(regionId: String): String? {
        return combinedState.value.downloadedRegions.find { it.id == regionId }?.status
    }
}
