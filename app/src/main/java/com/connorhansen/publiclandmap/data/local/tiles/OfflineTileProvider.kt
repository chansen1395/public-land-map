package com.connorhansen.publiclandmap.data.local.tiles

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineTileProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val openDatabases = mutableMapOf<String, SQLiteDatabase>()

    private fun getTilesDir(): File = File(context.filesDir, "tiles/offline")

    fun getAvailableMBTilesFiles(): List<File> {
        val dir = getTilesDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension == "mbtiles" }?.toList() ?: emptyList()
    }

    fun hasOfflineTiles(): Boolean = getAvailableMBTilesFiles().isNotEmpty()

    fun getTile(zoom: Int, x: Int, y: Int): ByteArray? {
        val tilesDir = getTilesDir()
        if (!tilesDir.exists()) return null

        val files = getAvailableMBTilesFiles()
        // TMS y-flip
        val tmsY = (1 shl zoom) - 1 - y

        for (file in files) {
            try {
                val db = openDatabase(file)
                val cursor = db.rawQuery(
                    "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                    arrayOf(zoom.toString(), x.toString(), tmsY.toString())
                )
                cursor.use {
                    if (it.moveToFirst()) {
                        return it.getBlob(0)
                    }
                }
            } catch (_: Exception) {
                // Try next file
            }
        }
        return null
    }

    fun coversArea(zoom: Int, minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): Boolean {
        val files = getAvailableMBTilesFiles()
        if (files.isEmpty()) return false

        for (file in files) {
            try {
                val db = openDatabase(file)
                val cursor = db.rawQuery("SELECT value FROM metadata WHERE name = 'bounds'", null)
                cursor.use {
                    if (it.moveToFirst()) {
                        val bounds = it.getString(0).split(",").map { v -> v.trim().toDouble() }
                        if (bounds.size == 4) {
                            val (bMinLng, bMinLat, bMaxLng, bMaxLat) = bounds
                            if (bMinLat <= minLat && bMaxLat >= maxLat &&
                                bMinLng <= minLng && bMaxLng >= maxLng
                            ) {
                                return true
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue
            }
        }
        return false
    }

    private fun openDatabase(file: File): SQLiteDatabase {
        return openDatabases.getOrPut(file.absolutePath) {
            SQLiteDatabase.openDatabase(
                file.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
        }
    }

    fun closeAll() {
        openDatabases.values.forEach { db ->
            try { db.close() } catch (_: Exception) {}
        }
        openDatabases.clear()
    }
}
