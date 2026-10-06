package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.service.CastSyncCoordinator
import com.theveloper.pixelplay.data.service.CastSyncPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** full（GMS）：Cast 同步端口绑真实协调器（SessionManager/MediaStatus 链路）。 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CastBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindCastSyncPort(impl: CastSyncCoordinator): CastSyncPort
}
