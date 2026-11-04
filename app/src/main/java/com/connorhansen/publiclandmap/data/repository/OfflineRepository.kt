package com.connorhansen.publiclandmap.data.repository

import android.content.Context
import com.connorhansen.publiclandmap.data.local.db.dao.OfflineRegionDao
import com.connorhansen.publiclandmap.data.mapper.OfflineRegionMapper
import com.connorhansen.publiclandmap.data.remote.api.OfflineApi
import com.connorhansen.publiclandmap.domain.model.OfflineRegion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRepository @Inject constructor(
    private val offlineRegionDao: OfflineRegionDao,
    private val offlineApi: OfflineApi,
    private val offlineRegionMapper: OfflineRegionMapper,
    @ApplicationContext private val context: Context
) {

    fun getAllRegions(): Flow<List<OfflineRegion>> {
        return offlineRegionDao.getAll().map { entities ->
            entities.map { offlineRegionMapper.entityToDomain(it) }
        }
    }

    suspend fun getRegionById(id: String): OfflineRegion? {
        return offlineRegionDao.getById(id)?.let { offlineRegionMapper.entityToDomain(it) }
    }

    suspend fun getAvailableRegions(): List<OfflineRegion> {
        return try {
            val dtos = offlineApi.getRegions()
            dtos.map { offlineRegionMapper.dtoToDomain(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveRegion(region: OfflineRegion) {
        offlineRegionDao.insert(offlineRegionMapper.domainToEntity(region))
    }

    suspend fun updateStatus(id: String, status: String) {
        offlineRegionDao.updateStatus(id, status)
    }

    suspend fun downloadRegion(regionId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            offlineRegionDao.updateStatus(regionId, "downloading")
            val responseBody = offlineApi.downloadRegion(regionId)
            val tilesDir = File(context.filesDir, "tiles/offline")
            tilesDir.mkdirs()
            val outputFile = File(tilesDir, "$regionId.mbtiles")

            responseBody.byteStream().use { inputStream ->
                outputFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                    }
                    offlineRegionDao.updateSizeBytes(regionId, totalBytesRead)
                }
            }

            offlineRegionDao.markComplete(regionId, System.currentTimeMillis())
            Result.success(outputFile)
        } catch (e: Exception) {
            offlineRegionDao.updateStatus(regionId, "error")
            Result.failure(e)
        }
    }

    suspend fun deleteRegion(id: String) {
        val tilesDir = File(context.filesDir, "tiles/offline")
        val file = File(tilesDir, "$id.mbtiles")
        if (file.exists()) {
            file.delete()
        }
        offlineRegionDao.delete(id)
    }

    suspend fun getCompletedRegions(): List<OfflineRegion> {
        return offlineRegionDao.getByStatus("complete")
            .map { offlineRegionMapper.entityToDomain(it) }
    }
}
