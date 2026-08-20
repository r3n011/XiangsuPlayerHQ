package com.theveloper.pixelplay.data.observer

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreObserver @Inject constructor(
    @ApplicationContext private val context: Context
) : ContentObserver(Handler(Looper.getMainLooper())), DefaultLifecycleObserver {

    private val _mediaStoreChanges = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val mediaStoreChanges: SharedFlow<Unit> = _mediaStoreChanges.asSharedFlow()

    private val _externalMediaStoreChanges = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val externalMediaStoreChanges: SharedFlow<Unit> = _externalMediaStoreChanges.asSharedFlow()

    @Volatile
    private var isRegistered: Boolean = false

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun register() {
        if (isRegistered) return
        context.contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            true,
            this
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            this
        )
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        context.contentResolver.unregisterContentObserver(this)
        isRegistered = false
    }

    override fun onStart(owner: LifecycleOwner) {
        register()
        // Android 10 Scoped Storage 可能导致系统 MediaStore 不及时索引新文件，
        // 应用回到前台时主动触发媒体扫描
        triggerMediaScan()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregister()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unregister()
        owner.lifecycle.removeObserver(this)
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        _mediaStoreChanges.tryEmit(Unit)
        _externalMediaStoreChanges.tryEmit(Unit)
    }

    fun forceRescan() {
        _mediaStoreChanges.tryEmit(Unit)
    }

    /**
     * Android 10 (API 29) Scoped Storage 下，系统 MediaStore 可能不会及时索引新文件。
     * 主动对标准音乐目录触发媒体扫描，确保新歌曲被发现。
     */
    private fun triggerMediaScan() {
        if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.Q) return // 仅 Android 10 需要
        try {
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_MUSIC
            )
            if (musicDir != null && musicDir.isDirectory) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(musicDir.absolutePath),
                    null
                ) { _, _ -> }
            }
        } catch (e: Exception) {
            // 忽略扫描失败
        }
    }
}
