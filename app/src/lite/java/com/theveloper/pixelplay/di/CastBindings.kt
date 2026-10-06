package com.theveloper.pixelplay.di

import com.theveloper.pixelplay.data.service.CastSyncPort
import com.theveloper.pixelplay.data.service.NoOpCastSyncPort
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** lite（no-gms）：Cast 同步端口绑 no-op 兜底。 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class CastBindingsModule {
    @Binds
    internal abstract fun bindCastSyncPort(impl: NoOpCastSyncPort): CastSyncPort
}
