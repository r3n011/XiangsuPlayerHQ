package com.theveloper.pixelplay.data.plugin

import timber.log.Timber

/**
 * 插件子系统统一日志入口。
 *
 * 所有 tag 以 `Xs` 开头，配合 `ReleaseTree` 的白名单：release 包也会输出 DEBUG/INFO，
 * 方便在真机上排查插件安装、脚本执行、Host API 调用与路由问题。
 */
object XsLog {
    const val MANAGER = "XsPluginManager"
    const val RUNTIME = "XsPluginRuntime"
    const val BRIDGE = "XsHostBridge"
    const val STORE = "XsStoreClient"
    const val IMPORTER = "XsZipImporter"
    const val REGISTRY = "XsPluginRegistry"
    const val WEB = "XsPluginWeb"
    const val ROUTER = "LxSourceRouter"

    fun d(tag: String, message: String) = Timber.tag(tag).d(message)

    fun d(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).d(message, *args)

    fun i(tag: String, message: String) = Timber.tag(tag).i(message)

    fun i(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).i(message, *args)

    fun w(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).w(message, *args)

    fun w(tag: String, t: Throwable?, message: String, vararg args: Any?) =
        Timber.tag(tag).w(t, message, *args)

    fun e(tag: String, t: Throwable?, message: String, vararg args: Any?) =
        Timber.tag(tag).e(t, message, *args)

    fun e(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).e(message, *args)
}
