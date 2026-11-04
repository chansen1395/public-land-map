package com.connorhansen.publiclandmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.connorhansen.publiclandmap.app.MainActivity
import com.connorhansen.publiclandmap.data.local.db.dao.OfflineRegionDao
import com.connorhansen.publiclandmap.data.remote.api.OfflineApi
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "download_channel"
        const val NOTIFICATION_ID = 1002
        const val ACTION_START_DOWNLOAD = "com.connorhansen.publiclandmap.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.connorhansen.publiclandmap.action.CANCEL_DOWNLOAD"
        const val EXTRA_REGION_ID = "extra_region_id"
        const val EXTRA_REGION_NAME = "extra_region_name"

        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 1000L

        private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
        val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

        private val _activeDownloads = MutableStateFlow<Set<String>>(emptySet())
        val activeDownloads: StateFlow<Set<String>> = _activeDownloads.asStateFlow()

        fun startDownload(context: Context, regionId: String, regionName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_REGION_ID, regionId)
                putExtra(EXTRA_REGION_NAME, regionName)
            }
            context.startForegroundService(intent)
        }

        fun cancelDownload(context: Context, regionId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_REGION_ID, regionId)
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var offlineApi: OfflineApi

    @Inject
    lateinit var offlineRegionDao: OfflineRegionDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadJobs = mutableMapOf<String, Job>()
    private var currentRegionName: String = "Map region"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val regionId = intent.getStringExtra(EXTRA_REGION_ID)
                    ?: return START_NOT_STICKY
                val regionName = intent.getStringExtra(EXTRA_REGION_NAME) ?: "Map region"
                currentRegionName = regionName
                startForeground(NOTIFICATION_ID, createProgressNotification(regionName, 0))
                startRegionDownload(regionId, regionName)
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val regionId = intent.getStringExtra(EXTRA_REGION_ID)
                    ?: return START_NOT_STICKY
                cancelRegionDownload(regionId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun startRegionDownload(regionId: String, regionName: String) {
        if (downloadJobs.containsKey(regionId)) return

        _activeDownloads.value = _activeDownloads.value + regionId
        updateProgress(regionId, 0f)

        val job = serviceScope.launch {
            for (attempt in 1..MAX_RETRIES) {
                try {
                    offlineRegionDao.updateStatus(regionId, "downloading")
                    performDownload(regionId, regionName)
                    offlineRegionDao.markComplete(regionId, System.currentTimeMillis())
                    removeProgress(regionId)
                    _activeDownloads.value = _activeDownloads.value - regionId
                    downloadJobs.remove(regionId)
                    stopIfIdle()
                    return@launch
                } catch (e: CancellationException) {
                    offlineRegionDao.updateStatus(regionId, "pending")
                    cleanupPartialDownload(regionId)
                    removeProgress(regionId)
                    _activeDownloads.value = _activeDownloads.value - regionId
                    downloadJobs.remove(regionId)
                    stopIfIdle()
                    throw e
                } catch (e: Exception) {
                    if (attempt < MAX_RETRIES) {
                        val backoff = INITIAL_BACKOFF_MS * (1L shl (attempt - 1))
                        delay(backoff)
                    }
                }
            }

            offlineRegionDao.updateStatus(regionId, "error")
            cleanupPartialDownload(regionId)
            removeProgress(regionId)
            _activeDownloads.value = _activeDownloads.value - regionId
            downloadJobs.remove(regionId)

            updateNotification("Download failed: $regionName", -1)
            delay(3000)
            stopIfIdle()
        }

        downloadJobs[regionId] = job
    }

    private suspend fun performDownload(regionId: String, regionName: String) {
        val responseBody = offlineApi.downloadRegion(regionId)
        val contentLength = responseBody.contentLength()

        val tilesDir = File(filesDir, "tiles/offline")
        tilesDir.mkdirs()

        val tempFile = File(tilesDir, "$regionId.mbtiles.tmp")
        val finalFile = File(tilesDir, "$regionId.mbtiles")

        try {
            responseBody.byteStream().use { inputStream ->
                tempFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        if (contentLength > 0) {
                            val progress = (totalBytesRead.toFloat() / contentLength).coerceIn(0f, 1f)
                            updateProgress(regionId, progress)
                            updateNotification(
                                "Downloading $regionName",
                                (progress * 100).toInt()
                            )
                        } else {
                            updateProgress(regionId, -1f)
                            updateNotification(
                                "Downloading $regionName (${formatBytes(totalBytesRead)})",
                                -1
                            )
                        }
                    }
                    offlineRegionDao.updateSizeBytes(regionId, totalBytesRead)
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    private fun cancelRegionDownload(regionId: String) {
        downloadJobs[regionId]?.cancel()
        downloadJobs.remove(regionId)
        _activeDownloads.value = _activeDownloads.value - regionId
        removeProgress(regionId)

        serviceScope.launch {
            offlineRegionDao.updateStatus(regionId, "pending")
            cleanupPartialDownload(regionId)
        }

        stopIfIdle()
    }

    private fun cleanupPartialDownload(regionId: String) {
        val tilesDir = File(filesDir, "tiles/offline")
        File(tilesDir, "$regionId.mbtiles.tmp").delete()
    }

    private fun stopIfIdle() {
        if (downloadJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateProgress(regionId: String, progress: Float) {
        _downloadProgress.value = _downloadProgress.value + (regionId to progress)
    }

    private fun removeProgress(regionId: String) {
        _downloadProgress.value = _downloadProgress.value - regionId
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Map Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress of offline map downloads"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createProgressNotification(regionName: String, progress: Int): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Maps")
            .setContentText("Downloading $regionName")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(tapPending)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun updateNotification(text: String, progress: Int) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading Maps")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(tapPending)

        if (progress >= 0) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.0f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
