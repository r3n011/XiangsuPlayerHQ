package com.theveloper.pixelplay.data.telegram

import android.content.Context
import com.theveloper.pixelplay.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramClientManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        @Volatile
        private var nativeLibraryLoaded: Boolean = false

        /**
         * TDLib native 库加载失败的原因（成功为 null）。
         * 登录页据此给出明确错误，不再静默吞掉后永远停在「初始化中」。
         */
        @Volatile
        var nativeLoadError: String? = null
            private set

        init {
            try {
                System.loadLibrary("tdjni")
                nativeLibraryLoaded = true
            } catch (e: Throwable) {
                // 捕获所有可能的异常：UnsatisfiedLinkError, ExceptionInInitializerError, etc.
                // 不崩溃（应用没有 TDLib 也能跑其它功能），但必须记录完整原因并暴露给 UI。
                nativeLoadError = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
                try {
                    android.util.Log.e("PixelPlay", "Failed to load TDLib native library", e)
                } catch (_: Throwable) {}
                nativeLibraryLoaded = false
            }
        }

        fun isNativeLibraryAvailable(): Boolean = nativeLibraryLoaded
    }

    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState = _authorizationState.asStateFlow()

    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 64)
    val updates = _updates.asSharedFlow()

    private val _errors = MutableSharedFlow<TdApi.Error>(extraBufferCapacity = 16)
    val errors = _errors.asSharedFlow()

    /**
     * TDLib 初始化失败原因（成功为 null）。
     * 初始值即 companion 的 [nativeLoadError]：native 库加载失败时登录页立刻可见。
     */
    private val _initFailure = MutableStateFlow(nativeLoadError)
    val initFailure = _initFailure.asStateFlow()

    private var client: Client? = null
    @Volatile
    private var recreateClientAfterClose = false

    // Handler for incoming updates from TDLib
    private val updateHandler = Client.ResultHandler { update ->
        if (update is TdApi.Update) {
            when (update) {
                is TdApi.UpdateAuthorizationState -> {
                    onAuthorizationStateUpdated(update.authorizationState)
                }
                is TdApi.UpdateUser -> {
                    // Handle user updates if needed
                }
                is TdApi.UpdateFile -> {
                    _updates.tryEmit(update)
                }
                // Add other update handlers here
                else -> {}
            }
        } else if (update is TdApi.Error) {
            reportTdError(update)
        }
    }

    init {
        // 确定性初始化：native 库加载成功时必须创建客户端（与原版行为一致）；
        // 创建失败记录完整堆栈并暴露到 [initFailure]，登录页可见，不再静默失能。
        initializeClient()
    }

    @Synchronized
    private fun initializeClient() {
        if (client != null) return
        if (!isNativeLibraryAvailable()) {
            // native 库没加载成功：原因已记录在 nativeLoadError 并已进入 initFailure 初始值
            return
        }
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (e: Throwable) {
            Timber.w(e, "Failed to set TDLib log verbosity")
        }

        try {
            client = Client.create(updateHandler, null, null)
        } catch (e: Throwable) {
            Timber.e(e, "Failed to create TDLib client")
            _initFailure.value = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
        }
    }

    private fun onAuthorizationStateUpdated(authState: TdApi.AuthorizationState) {
        _authorizationState.value = authState
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                val filesDirectory = File(context.filesDir, "tdlib_files").absolutePath
                
                // Based on error message and typical TDLib params structure for flat constructors:
                // useTestDc, databaseDir, filesDir, encryptionKey, useFileDatabase, useChatInfoDatabase, useMessageDatabase, useSecretChats, apiId, apiHash, systemLanguage, deviceModel, systemVersion, applicationVersion, enableStorageOptimizer, ignoreFileNames
                
                // Note: The order varies by version. I will try the most common flat signature.
                // If this fails, I might need to revert to using the object but finding why the object constructor failed.
                // Actually, often in Java bindings, you have to set fields on the object passed to SetTdlibParameters.
                // But if SetTdlibParameters ONLY has a multi-arg constructor, I must use it.
                
                // Let's assume the error message `constructor(p0: Boolean, p1: String!, ...)` matches the fields.
                
                client?.send(TdApi.SetTdlibParameters(
                    false, // useTestDc
                    databaseDirectory,
                    filesDirectory,
                    null, // databaseEncryptionKey
                    true, // useFileDatabase
                    true, // useChatInfoDatabase
                    true, // useMessageDatabase
                    false, // useSecretChats
                    BuildConfig.TELEGRAM_API_ID,
                    BuildConfig.TELEGRAM_API_HASH,
                    "en", // systemLanguageCode
                    "XiangsuPlayer Instance", // deviceModel
                    android.os.Build.VERSION.RELEASE, // systemVersion
                    BuildConfig.VERSION_NAME
                ), defaultHandler)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                // UI should prompt for phone number
            }
            is TdApi.AuthorizationStateWaitCode -> {
                // UI should prompt for code
            }
            is TdApi.AuthorizationStateReady -> {
                Timber.d("Telegram Client Ready")
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                Timber.d("Logging out")
            }
            is TdApi.AuthorizationStateClosing -> {
                Timber.d("Closing")
            }
            is TdApi.AuthorizationStateClosed -> {
                Timber.d("Closed")
                client = null
                if (recreateClientAfterClose) {
                    recreateClientAfterClose = false
                    initializeClient()
                }
            }
            else -> {}
        }
    }

    fun sendPhoneNumber(phoneNumber: String) {
        val settings = TdApi.PhoneNumberAuthenticationSettings()
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, settings), defaultHandler)
    }

    fun checkAuthenticationCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), defaultHandler)
    }
    
    fun checkAuthenticationPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password), defaultHandler)
    }

    fun logout() {
        recreateClientAfterClose = true
        client?.send(TdApi.LogOut(), defaultHandler)
    }

    fun closeClient(recreate: Boolean = false) {
        recreateClientAfterClose = recreate
        client?.send(TdApi.Close(), defaultHandler)
    }

    /**
     * General purpose suspend function to send requests to TDLib
     */
    suspend fun <T : TdApi.Object> sendRequest(function: TdApi.Function<*>): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val localClient = client
        if (localClient != null) {
            localClient.send(function) { result ->
                if (result is TdApi.Error) {
                    reportTdError(result)
                    continuation.resumeWith(
                        Result.failure(
                            TdlibRequestException(
                                code = result.code,
                                rawMessage = result.message
                            )
                        )
                    )
                } else {
                    @Suppress("UNCHECKED_CAST")
                    continuation.resumeWith(Result.success(result as T))
                }
            }
        } else {
            continuation.resumeWith(Result.failure(IllegalStateException("Telegram Client is not initialized")))
        }
    }

    private val defaultHandler = Client.ResultHandler { result ->
        if (result is TdApi.Error) {
            reportTdError(result)
        }
    }

    private fun reportTdError(error: TdApi.Error) {
        _errors.tryEmit(error)
        Timber.e("TDLib Error: ${error.code} - ${error.message}")
    }

    /**
     * Quick check if TDLib is ready to process requests.
     */
    fun isReady(): Boolean = _authorizationState.value is TdApi.AuthorizationStateReady

    /**
     * Suspends until the TDLib client reaches AuthorizationStateReady.
     * @param timeoutMs Maximum time to wait (default 30 seconds)
     * @return true if ready, false if timed out or closed
     */
    suspend fun awaitReady(timeoutMs: Long = 30_000L): Boolean {
        // Quick check first
        if (isReady()) return true
        
        return try {
            withTimeoutOrNull(timeoutMs) {
                authorizationState.first { state ->
                    state is TdApi.AuthorizationStateReady ||
                    state is TdApi.AuthorizationStateClosed
                }
            } is TdApi.AuthorizationStateReady
        } catch (e: Exception) {
            Timber.w("awaitReady failed: ${e.message}")
            false
        }
    }
}

class TdlibRequestException(
    val code: Int,
    rawMessage: String?
) : Exception(rawMessage ?: "Unknown TDLib error")
