package com.connorhansen.publiclandmap.data.repository

import com.connorhansen.publiclandmap.data.local.db.dao.TrackDao
import com.connorhansen.publiclandmap.data.local.db.dao.TrackPointDao
import com.connorhansen.publiclandmap.data.mapper.TrackMapper
import com.connorhansen.publiclandmap.domain.model.Track
import com.connorhansen.publiclandmap.domain.model.TrackPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackRepository @Inject constructor(
    private val trackDao: TrackDao,
    private val trackPointDao: TrackPointDao,
    private val trackMapper: TrackMapper
) {

    fun getAllTracks(): Flow<List<Track>> {
        return trackDao.getAll().map { entities ->
            entities.map { trackMapper.entityToDomainWithoutPoints(it) }
        }
    }

    suspend fun getTrackById(id: String): Track? {
        val trackEntity = trackDao.getById(id) ?: return null
        val pointEntities = trackPointDao.getByTrackId(id)
        return trackMapper.entityToDomain(trackEntity, pointEntities)
    }

    suspend fun saveTrack(track: Track) {
        trackDao.insert(trackMapper.domainToEntity(track))
        if (track.points.isNotEmpty()) {
            val pointEntities = track.points.map { trackMapper.pointDomainToEntity(track.id, it) }
            trackPointDao.insertBatch(pointEntities)
        }
    }

    suspend fun updateTrack(track: Track) {
        trackDao.update(trackMapper.domainToEntity(track))
    }

    suspend fun deleteTrack(id: String) {
        trackPointDao.deleteByTrackId(id)
        trackDao.deleteById(id)
    }

    suspend fun addTrackPoints(trackId: String, points: List<TrackPoint>) {
        val entities = points.map { trackMapper.pointDomainToEntity(trackId, it) }
        trackPointDao.insertBatch(entities)
    }

    suspend fun addTrackPoint(trackId: String, point: TrackPoint) {
        trackPointDao.insert(trackMapper.pointDomainToEntity(trackId, point))
    }

    suspend fun getTrackPoints(trackId: String): List<TrackPoint> {
        return trackPointDao.getByTrackId(trackId).map { trackMapper.pointEntityToDomain(it) }
    }

    suspend fun getTrackPointCount(trackId: String): Int {
        return trackPointDao.getPointCount(trackId)
    }
}
