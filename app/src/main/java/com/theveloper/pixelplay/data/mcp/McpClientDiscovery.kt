package com.theveloper.pixelplay.data.mcp

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

data class CompatibleClient(
    val packageName: String,
    val label: String,
    val scheme: String,
)

/**
 * Finds installed AI clients that opted in to the one-tap pairing contract:
 *
 *   <intent-filter>
 *     <action android:name="com.pixelplayer.mcp.CLIENT" />
 *   </intent-filter>
 *   <meta-data android:name="com.pixelplayer.mcp.PAIR_SCHEME" android:value="cherrystudio" />
 *
 * The player also needs a <queries> entry in its manifest (Android 11+ package visibility).
 */
@Singleton
class McpClientDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val ACTION_MCP_CLIENT = "com.pixelplayer.mcp.CLIENT"
        const val META_PAIR_SCHEME = "com.pixelplayer.mcp.PAIR_SCHEME"
        const val META_DISPLAY_NAME = "com.pixelplayer.mcp.DISPLAY_NAME"
    }

    fun listCompatible(): List<CompatibleClient> {
        val pm = context.packageManager
        val intent = Intent(ACTION_MCP_CLIENT).addCategory(Intent.CATEGORY_DEFAULT)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapNotNull { resolve ->
                val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
                val appInfo = runCatching {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                }.getOrNull() ?: return@mapNotNull null
                val activityMeta = resolve.activityInfo?.metaData
                val appMeta = appInfo.metaData
                val scheme = (activityMeta?.getString(META_PAIR_SCHEME)
                    ?: appMeta?.getString(META_PAIR_SCHEME))
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = (activityMeta?.getString(META_DISPLAY_NAME)
                    ?: appMeta?.getString(META_DISPLAY_NAME))
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(pkg)
                CompatibleClient(pkg, label, scheme)
            }.distinctBy { it.packageName }
        }.onFailure {
            Timber.w(it, "MCP: failed to query compatible clients")
        }.getOrDefault(emptyList())
    }
}
