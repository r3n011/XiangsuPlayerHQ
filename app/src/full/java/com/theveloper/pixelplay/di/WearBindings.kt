package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.service.wear.WearPhoneTransferSender
import com.theveloper.pixelplay.data.service.wear.WearStatePublisher
import com.theveloper.pixelplay.data.service.wear.WearStatePublisherPort
import com.theveloper.pixelplay.data.service.wear.WearSongTransferPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** full（GMS）：手表传输端口绑真实实现（play-services-wearable 传输链路）。 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WearBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindWearSongTransferPort(impl: WearPhoneTransferSender): WearSongTransferPort

    @Binds
    @Singleton
    internal abstract fun bindWearStatePublisherPort(impl: WearStatePublisher): WearStatePublisherPort
}
