package com.connorhansen.publiclandmap.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.Waypoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class GpxExporter @Inject constructor() {

    companion object {
        private const val GPX_HEADER = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Public Land Map"
  xmlns="http://www.topografix.com/GPX/1/1"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
        private const val GPX_FOOTER = "\n</gpx>"
    }

    fun exportWaypoints(
        context: Context,
        waypoints: List<Waypoint>,
        fileName: String = "waypoints"
    ): File = write(
        context, "$fileName.gpx",
        buildGpx(fileName, System.currentTimeMillis(), waypoints, emptyList())
    )

    fun exportTrack(
        context: Context,
        track: Track,
        fileName: String? = null
    ): File {
        val safeName = fileName ?: track.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return write(
            context, "$safeName.gpx",
            buildGpx(track.name, track.createdAt, emptyList(), listOf(track))
        )
    }

    fun exportAll(
        context: Context,
        waypoints: List<Waypoint>,
        tracks: List<Track>,
        fileName: String = "public_land_map_export"
    ): File = write(
        context, "$fileName.gpx",
        buildGpx(fileName, System.currentTimeMillis(), waypoints, tracks)
    )

    fun createShareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Builds a GPX 1.1 document. [metadataName] and [metadataTimeMillis] go in the
     * metadata block; waypoints come first, then one trk element per track.
     */
    fun buildGpx(
        metadataName: String,
        metadataTimeMillis: Long,
        waypoints: List<Waypoint>,
        tracks: List<Track>
    ): String {
        val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fun time(millis: Long) = isoDateFormat.format(Date(millis))

        return buildString {
            append(GPX_HEADER)
            append("\n  <metadata>")
            append("\n    <name>${escapeXml(metadataName)}</name>")
            append("\n    <time>${time(metadataTimeMillis)}</time>")
            append("\n  </metadata>")

            for (wpt in waypoints) {
                append("\n  <wpt lat=\"${wpt.latitude}\" lon=\"${wpt.longitude}\">")
                wpt.elevation?.let { append("\n    <ele>$it</ele>") }
                append("\n    <time>${time(wpt.createdAt)}</time>")
                append("\n    <name>${escapeXml(wpt.name)}</name>")
                wpt.description?.let { append("\n    <desc>${escapeXml(it)}</desc>") }
                append("\n    <sym>${escapeXml(wpt.icon)}</sym>")
                append("\n  </wpt>")
            }

            for (track in tracks) {
                append("\n  <trk>")
                append("\n    <name>${escapeXml(track.name)}</name>")
                track.description?.let { append("\n    <desc>${escapeXml(it)}</desc>") }
                append("\n    <trkseg>")
                for (pt in track.points) {
                    append("\n      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
                    pt.elevation?.let { append("\n        <ele>$it</ele>") }
                    append("\n        <time>${time(pt.timestamp)}</time>")
                    append("\n      </trkpt>")
                }
                append("\n    </trkseg>")
                append("\n  </trk>")
            }

            append(GPX_FOOTER)
        }
    }

    private fun write(context: Context, name: String, content: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val gpxDir = File(dir, "gpx_exports")
        if (!gpxDir.exists()) gpxDir.mkdirs()
        return File(gpxDir, name).apply { writeText(content) }
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
