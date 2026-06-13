package com.theveloper.pixelplay.data.lx

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LxFileStore @Inject constructor(
    @ApplicationContext private val appContext: Context
) {
    private val dir: File
        get() = File(appContext.filesDir, "lx_user_js").also { it.mkdirs() }

    fun defaultJsFile(): File = File(dir, "userapi.js")

    suspend fun writeFromUri(uri: Uri, target: File = defaultJsFile()): Boolean =
        withContext(Dispatchers.IO) {
            try {
                appContext.contentResolver.openInputStream(uri)?.use { src ->
                    target.outputStream().use { dst -> src.copyTo(dst) }
                }
                target.exists()
            } catch (t: Throwable) {
                false
            }
        }

    suspend fun writeFromUrl(url: String, target: File = defaultJsFile()): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                val req = Request.Builder().url(url).get().build()
                val resp = client.newCall(req).execute()
                if (!resp.isSuccessful) return@withContext false
                resp.body?.use { body ->
                    target.outputStream().use { dst -> body.byteStream().copyTo(dst) }
                }
                target.exists() && target.length() > 200
            } catch (t: Throwable) {
                false
            }
        }

    suspend fun content(file: File = defaultJsFile()): String? =
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) null else file.readText()
            } catch (t: Throwable) { null }
        }

    fun delete(): Boolean {
        val f = defaultJsFile()
        return !f.exists() || f.delete()
    }

    fun exists(): Boolean = defaultJsFile().exists()

    suspend fun headJs(): String? = withContext(Dispatchers.IO) {
        val file = defaultJsFile()
        if (!file.exists()) return@withContext null
        val content = file.readText()
        val lines = content.lineSequence().take(20).joinToString("\n")
        lines
    }
}
