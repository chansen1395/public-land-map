package com.connorhansen.publiclandmap.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.connorhansen.publiclandmap.data.local.db.dao.OfflineRegionDao
import com.connorhansen.publiclandmap.data.local.db.dao.ParcelDao
import com.connorhansen.publiclandmap.data.local.db.dao.PublicLandDao
import com.connorhansen.publiclandmap.data.local.db.dao.RegulationDao
import com.connorhansen.publiclandmap.data.local.db.dao.TrackDao
import com.connorhansen.publiclandmap.data.local.db.dao.TrackPointDao
import com.connorhansen.publiclandmap.data.local.db.dao.WaypointDao
import com.connorhansen.publiclandmap.data.local.db.entity.OfflineRegionEntity
import com.connorhansen.publiclandmap.data.local.db.entity.ParcelEntity
import com.connorhansen.publiclandmap.data.local.db.entity.PublicLandEntity
import com.connorhansen.publiclandmap.data.local.db.entity.RegulationEntity
import com.connorhansen.publiclandmap.data.local.db.entity.TrackEntity
import com.connorhansen.publiclandmap.data.local.db.entity.TrackPointEntity
import com.connorhansen.publiclandmap.data.local.db.entity.WaypointEntity

@Database(
    entities = [
        ParcelEntity::class,
        PublicLandEntity::class,
        RegulationEntity::class,
        WaypointEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
        OfflineRegionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun parcelDao(): ParcelDao
    abstract fun publicLandDao(): PublicLandDao
    abstract fun regulationDao(): RegulationDao
    abstract fun waypointDao(): WaypointDao
    abstract fun trackDao(): TrackDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun offlineRegionDao(): OfflineRegionDao
}
