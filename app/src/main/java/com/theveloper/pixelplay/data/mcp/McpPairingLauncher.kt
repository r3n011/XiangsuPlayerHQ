package com.theveloper.pixelplay.data.mcp

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Launches the paired AI client and brings it back after approval.
 *
 * `setPackage` is mandatory: a bare ACTION_VIEW for a custom scheme can be intercepted by a
 * browser or any other app that registered the same scheme.
 */
@Singleton
class McpPairingLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun launchClient(packageName: String, scheme: String, payload: String): Boolean {
        val uri = Uri.parse("$scheme://mcp/pair?p=$payload")
        return launch(packageName, uri)
    }

    fun launchCallback(scheme: String, payload: String): Boolean {
        val uri = Uri.parse("$scheme://mcp/paired?p=$payload")
        return launch(null, uri)
    }

    private fun launch(packageName: String?, uri: Uri): Boolean {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (packageName != null) intent.setPackage(packageName)
            context.startActivity(intent)
            true
        }.onFailure {
            Timber.w(it, "MCP: failed to launch %s", uri)
        }.getOrDefault(false)
    }
}
