package com.connorhansen.publiclandmap.domain.usecase

import com.connorhansen.publiclandmap.data.repository.TrackRepository
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.TrackPoint
import com.connorhansen.publiclandmap.util.DistanceCalculator
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class RecordTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
    private val distanceCalculator: DistanceCalculator
) {
    suspend fun startRecording(name: String): Track {
        val track = Track(
            id = UUID.randomUUID().toString(),
            name = name,
            description = null,
            distanceMeters = 0.0,
            elevationGain = 0.0,
            durationMs = 0L,
            createdAt = System.currentTimeMillis()
        )
        trackRepository.saveTrack(track)
        return track
    }

    suspend fun addPoint(
        trackId: String,
        latitude: Double,
        longitude: Double,
        elevation: Double?,
        accuracy: Float?,
        timestamp: Long
    ) {
        val pointCount = trackRepository.getTrackPointCount(trackId)
        val point = TrackPoint(
            latitude = latitude,
            longitude = longitude,
            elevation = elevation,
            timestamp = timestamp,
            accuracy = accuracy,
            sequence = pointCount
        )
        trackRepository.addTrackPoint(trackId, point)
    }

    suspend fun stopRecording(trackId: String): Track? {
        val track = trackRepository.getTrackById(trackId) ?: return null
        val points = track.points
        if (points.size < 2) return track

        var totalDistance = 0.0
        var totalElevationGain = 0.0
        for (i in 1 until points.size) {
            totalDistance += distanceCalculator.haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
            val prev = points[i - 1].elevation
            val curr = points[i].elevation
            if (prev != null && curr != null && curr > prev) {
                totalElevationGain += (curr - prev)
            }
        }

        val durationMs = if (points.isNotEmpty()) {
            points.last().timestamp - points.first().timestamp
        } else {
            0L
        }

        val updatedTrack = track.copy(
            distanceMeters = totalDistance,
            elevationGain = totalElevationGain,
            durationMs = durationMs
        )
        trackRepository.updateTrack(updatedTrack)
        return updatedTrack
    }

    suspend fun deleteTrack(id: String) {
        trackRepository.deleteTrack(id)
    }

    fun getAllTracks(): Flow<List<Track>> {
        return trackRepository.getAllTracks()
    }

    suspend fun getTrackById(id: String): Track? {
        return trackRepository.getTrackById(id)
    }
}
