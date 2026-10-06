package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.service.wear.NoOpWearSongTransferPort
import com.theveloper.pixelplay.data.service.wear.NoOpWearStatePublisher
import com.theveloper.pixelplay.data.service.wear.WearStatePublisherPort
import com.theveloper.pixelplay.data.service.wear.WearSongTransferPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** lite（no-gms）：手表传输端口绑 no-op 兜底（恒不可用）。 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class WearBindingsModule {
    @Binds
    internal abstract fun bindWearSongTransferPort(impl: NoOpWearSongTransferPort): WearSongTransferPort

    @Binds
    internal abstract fun bindWearStatePublisherPort(impl: NoOpWearStatePublisher): WearStatePublisherPort
}
