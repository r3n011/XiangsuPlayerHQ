package com.theveloper.pixelplay.data.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 插件目录约定（全部在应用私有目录，插件互相不可见）。 */
@Singleton
class XsPluginPaths @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val pluginsRoot: File get() = File(context.filesDir, "xs-plugins").apply { mkdirs() }
    val downloadDir: File get() = File(context.cacheDir, "xs-plugin-download").apply { mkdirs() }

    fun pluginDir(pluginId: String): File = File(pluginsRoot, pluginId)

    fun manifestFile(pluginId: String): File = File(pluginDir(pluginId), "manifest.json")

    fun mainScript(pluginId: String): File = File(pluginDir(pluginId), "main.js")

    fun agreementFile(pluginId: String, relative: String): File = File(pluginDir(pluginId), relative)

    fun uiEntry(pluginId: String, entry: String): File = File(pluginDir(pluginId), entry)

    fun tempZip(pluginId: String, version: String): File =
        File(downloadDir, "$pluginId-$version.zip.part")
}
