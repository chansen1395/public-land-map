package com.connorhansen.publiclandmap.di

import android.content.Context
import androidx.room.Room
import com.connorhansen.publiclandmap.data.local.db.AppDatabase
import com.connorhansen.publiclandmap.data.local.db.MIGRATION_4_5
import com.connorhansen.publiclandmap.data.local.db.MIGRATION_5_6
import com.connorhansen.publiclandmap.data.local.db.dao.OfflineRegionDao
import com.connorhansen.publiclandmap.data.local.db.dao.ParcelDao
import com.connorhansen.publiclandmap.data.local.db.dao.PublicLandDao
import com.connorhansen.publiclandmap.data.local.db.dao.RegulationDao
import com.connorhansen.publiclandmap.data.local.db.dao.TrackDao
import com.connorhansen.publiclandmap.data.local.db.dao.TrackPointDao
import com.connorhansen.publiclandmap.data.local.db.dao.WaypointDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "public_land_map.db"
        )
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideParcelDao(db: AppDatabase): ParcelDao = db.parcelDao()

    @Provides
    fun providePublicLandDao(db: AppDatabase): PublicLandDao = db.publicLandDao()

    @Provides
    fun provideRegulationDao(db: AppDatabase): RegulationDao = db.regulationDao()

    @Provides
    fun provideWaypointDao(db: AppDatabase): WaypointDao = db.waypointDao()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideTrackPointDao(db: AppDatabase): TrackPointDao = db.trackPointDao()

    @Provides
    fun provideOfflineRegionDao(db: AppDatabase): OfflineRegionDao = db.offlineRegionDao()
}
