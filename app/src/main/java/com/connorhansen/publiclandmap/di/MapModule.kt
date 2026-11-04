package com.connorhansen.publiclandmap.di

import android.content.Context
import com.connorhansen.publiclandmap.data.local.tiles.LocalTileServer
import com.connorhansen.publiclandmap.data.local.tiles.OfflineTileProvider
import com.connorhansen.publiclandmap.util.ConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MapModule {

    @Provides
    @Singleton
    fun provideOfflineTileProvider(@ApplicationContext context: Context): OfflineTileProvider {
        return OfflineTileProvider(context)
    }

    @Provides
    @Singleton
    fun provideLocalTileServer(
        @ApplicationContext context: Context,
        offlineTileProvider: OfflineTileProvider
    ): LocalTileServer {
        return LocalTileServer(context, offlineTileProvider)
    }

    @Provides
    @Singleton
    fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver {
        return ConnectivityObserver(context)
    }
}
