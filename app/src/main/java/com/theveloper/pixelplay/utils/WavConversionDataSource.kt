package com.theveloper.pixelplay.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber
import java.io.File

@UnstableApi
class WavConversionDataSource private constructor(
    private val data: ByteArray,
    private val uri: Uri?
) : DataSource {

    private var readPosition: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        val position = dataSpec.position
        val length = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            data.size.toLong() - position
        }
        readPosition = position.coerceAtLeast(0)
        Timber.tag(TAG).d("open(): position=$position, length=$length, dataSize=${data.size}")
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readPosition >= data.size) return -1

        val bytesToRead = minOf(readLength, (data.size - readPosition).toInt())
        System.arraycopy(data, readPosition.toInt(), buffer, offset, bytesToRead)
        readPosition += bytesToRead
        return bytesToRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        Timber.tag(TAG).d("close()")
    }

    override fun addTransferListener(transferListener: TransferListener) {
    }

    companion object {
        private const val TAG = "WavConversionDS"

        fun Factory(
            context: Context,
            originalFactory: DataSource.Factory
        ): DataSource.Factory {
            return WavConversionFactory(context.applicationContext, originalFactory)
        }
    }

    private class WavConversionFactory(
        private val context: Context,
        private val originalFactory: DataSource.Factory
    ) : DataSource.Factory {

        override fun createDataSource(): DataSource {
            return WavConversionDataSourceDelegate(context, originalFactory)
        }
    }

    private class WavConversionDataSourceDelegate(
        private val context: Context,
        private val originalFactory: DataSource.Factory
    ) : DataSource {

        private var miniaudioSource: MiniaudioDataSource? = null
        private var convertedSource: DataSource? = null
        private var delegateDataSource: DataSource? = null
        private var convertedTempFile: File? = null
        private var lastUri: Uri? = null
        private var lastFilePath: String? = null
        private var isCachedFile: Boolean = false

        private fun ensureDelegate(): DataSource {
            if (delegateDataSource == null) {
                delegateDataSource = originalFactory.createDataSource()
            }
            return delegateDataSource!!
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            lastUri = uri

            val filePath = resolveFilePath(uri)
            lastFilePath = filePath

            if (filePath != null) {
                Timber.tag(TAG).d("open() uri=$uri, filePath=$filePath")
                val file = File(filePath)
                if (file.exists()) {
                    val ext = file.extension.lowercase()
                    if (isHiFiFormat(ext)) {
                        Timber.tag(TAG).i("HiFi file detected: ${file.name} (ext=$ext, ${file.length()} bytes)")
                        return tryDecoding(filePath, uri, dataSpec)
                    }
                }
            }

            Timber.tag(TAG).d("Not a HiFi format, using delegate: $uri")
            return ensureDelegate().open(dataSpec)
        }

        private val WAV_EXTENSIONS = setOf("wav", "wave", "rf64")
        private val DFF_EXTENSIONS = setOf("dff", "dsd", "dif")

        private fun isHiFiFormat(ext: String): Boolean = ext in WAV_EXTENSIONS || ext in DFF_EXTENSIONS

        private fun tryDecoding(
            filePath: String,
            uri: Uri?,
            dataSpec: DataSpec
        ): Long {
            val file = File(filePath)
            val ext = file.extension.lowercase()

            // 初始化缓存管理器
            TranscodeCacheManager.init(context)

            // 先检查缓存
            TranscodeCacheManager.getCachedFile(filePath)?.let { cachedFile ->
                Timber.tag(TAG).i("Using cached transcoded file for ${file.name}")
                convertedTempFile = cachedFile
                isCachedFile = true

                val tempUri = Uri.fromFile(cachedFile)
                val tempDataSpec = DataSpec.Builder()
                    .setUri(tempUri)
                    .setPosition(dataSpec.position)
                    .setLength(dataSpec.length)
                    .build()

                convertedSource = originalFactory.createDataSource()
                val result = convertedSource!!.open(tempDataSpec)
                Timber.tag(TAG).i("Cached file opened, totalBytes=$result, position=${dataSpec.position}")
                return result
            }

            // Step 1: Try miniaudio streaming (zero-copy, no temp file)
            Timber.tag(TAG).i("Step 1: Trying miniaudio streaming for ${file.name}")
            try {
                miniaudioSource = MiniaudioDataSource(
                    filePath, uri,
                    MiniaudioDecoder.FORMAT_S16, 0, 0
                )
                val result = miniaudioSource!!.open(dataSpec)
                Timber.tag(TAG).i("miniaudio streaming succeeded, totalBytes=$result")
                return result
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "miniaudio streaming failed for ${file.name}")
                miniaudioSource = null
            }

            // Step 2: miniaudio decode to temp file (works for ANY format miniaudio supports)
            Timber.tag(TAG).i("Step 2: Trying miniaudio decode-to-file for ${file.name}")
            val tempDir = File(context.cacheDir, "wav_converted")
            tempDir.mkdirs()
            // 不再清空临时目录，保留缓存文件

            val decoder = try {
                MiniaudioDecoder.open(filePath, MiniaudioDecoder.FORMAT_S16, 0, 0)
            } catch (e: Throwable) {
                Timber.tag(TAG).w(e, "miniaudio decoder open failed for ${file.name}")
                null
            }

            if (decoder != null) {
                val tempFile = try {
                    decoder.decodeToTempWav(tempDir, file.nameWithoutExtension)
                } finally {
                    decoder.close()
                }

                if (tempFile != null && tempFile.exists() && tempFile.length() > 44) {
                    // 保存到缓存
                    val cachedFile = TranscodeCacheManager.cacheTranscodedFile(filePath, tempFile)
                    val fileToUse = cachedFile ?: tempFile
                    
                    convertedTempFile = fileToUse
                    isCachedFile = cachedFile != null
                    Timber.tag(TAG).i("Step 2: Decoded to ${fileToUse.name} (${fileToUse.length()} bytes)")

                    val tempUri = Uri.fromFile(fileToUse)
                    val tempDataSpec = DataSpec.Builder()
                        .setUri(tempUri)
                        .setPosition(dataSpec.position)
                        .setLength(dataSpec.length)
                        .build()

                    convertedSource = originalFactory.createDataSource()
                    val result = convertedSource!!.open(tempDataSpec)
                    Timber.tag(TAG).i("Step 2: temp file opened, totalBytes=$result")
                    return result
                }
            }

            // Step 3: DFF/DSD fallback (pure Kotlin DSD→PCM decoder)
            if (ext in DFF_EXTENSIONS) {
                Timber.tag(TAG).i("Step 3: Trying DFF/DSD decoder for ${file.name}")

                // 设置进度回调
                DffDecoder.progressCallback = { progress, stage ->
                    Timber.tag(TAG).i("[TRANSCODE] $progress% - $stage")
                }

                // 使用缓存锁防止并发转码
                val cacheLock = TranscodeCacheManager.getCacheLock(filePath)
                val tempFile = synchronized(cacheLock) {
                    try {
                        // 再次检查缓存（可能其他线程已经转码完成）
                        TranscodeCacheManager.getCachedFile(filePath)?.let { cached ->
                            return@synchronized cached
                        }
                        DffDecoder.decodeToWav(filePath, tempDir)
                    } catch (e: Throwable) {
                        Timber.tag(TAG).e(e, "DFF decoder failed for ${file.name}")
                        null
                    } finally {
                        DffDecoder.progressCallback = null
                    }
                }

                if (tempFile != null && tempFile.exists() && tempFile.length() > 44) {
                    // 保存到缓存
                    val cachedFile = TranscodeCacheManager.cacheTranscodedFile(filePath, tempFile)
                    val fileToUse = cachedFile ?: tempFile
                    
                    convertedTempFile = fileToUse
                    isCachedFile = cachedFile != null
                    Timber.tag(TAG).i("Step 3: DFF decoded to ${fileToUse.name} (${fileToUse.length()} bytes)")

                    val tempUri = Uri.fromFile(fileToUse)
                    val tempDataSpec = DataSpec.Builder()
                        .setUri(tempUri)
                        .setPosition(dataSpec.position)
                        .setLength(dataSpec.length)
                        .build()

                    convertedSource = originalFactory.createDataSource()
                    val result = convertedSource!!.open(tempDataSpec)
                    Timber.tag(TAG).i("Step 3: DFF temp file opened, totalBytes=$result")
                    return result
                }
            }

            // Step 4: For standard WAV, try Kotlin-based conversion
            if (ext in WAV_EXTENSIONS) {
                Timber.tag(TAG).i("Step 4: Trying Kotlin WavConverter for ${file.name}")
                val needsConv = runCatching {
                    val variant = WavConverter.detectVariant(filePath)
                    Timber.tag(TAG).i("WAV variant=$variant for ${file.name}")
                    when (variant) {
                        WavConverter.WavVariant.RF64, WavConverter.WavVariant.WAVE64 -> true
                        WavConverter.WavVariant.STANDARD -> {
                            val info = WavConverter.readWavInfo(file)
                            if (info != null) {
                                Timber.tag(TAG).i(
                                    "WAV info: formatTag=${info.formatTag}, channels=${info.numChannels}, " +
                                        "rate=${info.sampleRate}, bits=${info.bitsPerSample}"
                                )
                                info.formatTag != WavConverter.FORMAT_PCM || info.bitsPerSample > 32
                            } else true
                        }
                        WavConverter.WavVariant.UNSUPPORTED -> true
                    }
                }.getOrDefault(false)

                if (needsConv) {
                    return tryFfmpegFallback(filePath, uri, dataSpec)
                }
            }

            // Step 5: No conversion needed — use delegate
            Timber.tag(TAG).i("No conversion needed, using delegate for ${file.name}")
            return ensureDelegate().open(dataSpec)
        }

        private fun tryFfmpegFallback(
            filePath: String,
            uri: Uri?,
            dataSpec: DataSpec
        ): Long {
            val file = File(filePath)
            Timber.tag(TAG).i("FFmpeg fallback: creating temp converted file for ${file.name}")

            return try {
                val tempDir = File(context.cacheDir, "wav_converted")
                tempDir.mkdirs()

                // Clean up old temp files
                tempDir.listFiles()?.forEach { it.delete() }

                val tempPath = WavConverter.createTempConvertedFile(filePath, tempDir)
                if (tempPath == null) {
                    Timber.tag(TAG).w("FFmpeg fallback: createTempConvertedFile returned null")
                    return ensureDelegate().open(dataSpec)
                }

                val tempFile = File(tempPath)
                convertedTempFile = tempFile
                Timber.tag(TAG).i("FFmpeg fallback: converted to ${tempFile.name} (${tempFile.length()} bytes)")

                // Open the temp file through a FileDataSource
                val tempUri = Uri.fromFile(tempFile)
                val tempDataSpec = DataSpec.Builder()
                    .setUri(tempUri)
                    .setPosition(dataSpec.position)
                    .setLength(dataSpec.length)
                    .build()

                convertedSource = originalFactory.createDataSource()
                val result = convertedSource!!.open(tempDataSpec)
                Timber.tag(TAG).i("FFmpeg fallback: temp file opened, totalBytes=$result")
                result
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "FFmpeg fallback failed, trying delegate")
                convertedTempFile?.delete()
                convertedTempFile = null
                convertedSource = null
                ensureDelegate().open(dataSpec)
            }
        }

        private fun resolveFilePath(uri: Uri?): String? {
            if (uri == null) return null
            return when (uri.scheme) {
                "file" -> uri.path?.let(::decodeUriPath)
                "content" -> resolveContentFilePath(uri)
                else -> null
            }
        }

        private fun decodeUriPath(path: String): String {
            return try {
                java.net.URLDecoder.decode(path, "UTF-8")
            } catch (e: Exception) {
                path
            }
        }

        private fun resolveContentFilePath(uri: Uri): String? {
            return runCatching {
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                val cursor = context.contentResolver.query(
                    uri, projection, null, null, null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val dataIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (dataIndex >= 0) {
                            val path = it.getString(dataIndex)
                            if (!path.isNullOrEmpty()) return path
                        }
                    }
                }
                null
            }.getOrNull()
        }

        override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
            miniaudioSource?.let { return it.read(buffer, offset, readLength) }
            convertedSource?.let { return it.read(buffer, offset, readLength) }
            return ensureDelegate().read(buffer, offset, readLength)
        }

        override fun getUri(): Uri? {
            return miniaudioSource?.uri ?: convertedSource?.uri ?: lastUri
        }

        override fun close() {
            Timber.tag(TAG).d("close() source=${when {
                miniaudioSource != null -> "miniaudio"
                convertedSource != null -> "ffmpeg_fallback"
                else -> "delegate"
            }}, isCached=$isCachedFile")
            miniaudioSource?.close()
            miniaudioSource = null
            convertedSource?.close()
            convertedSource = null
            delegateDataSource?.close()
            delegateDataSource = null
            
            // 只删除非缓存的临时文件（miniaudio 生成的临时文件）
            // 缓存文件保留在 TranscodeCacheManager 管理的缓存目录中
            if (!isCachedFile) {
                convertedTempFile?.delete()
            }
            convertedTempFile = null
            isCachedFile = false
            lastUri = null
            lastFilePath = null
        }

        override fun addTransferListener(transferListener: TransferListener) {
            delegateDataSource?.addTransferListener(transferListener)
                ?: originalFactory.createDataSource().addTransferListener(transferListener)
        }
    }
}