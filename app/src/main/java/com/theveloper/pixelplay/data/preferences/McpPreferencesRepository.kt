package com.theveloper.pixelplay.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistence for the standalone MCP bridge.
 *
 * Deliberately separate from Web Remote: its own enabled flag, port, token store and
 * device id. Nothing here is read by WebRemoteServerService.
 */
@Singleton
class McpPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        const val DEFAULT_PORT = 8787
        const val PORT_RANGE_START = 8787
        const val PORT_RANGE_END = 8799

        private val KEY_ENABLED = booleanPreferencesKey("mcp_enabled")
        private val KEY_PORT = intPreferencesKey("mcp_port")
        private val KEY_CLIENTS_JSON = stringPreferencesKey("mcp_clients_json")
        private val KEY_DEVICE_ID = stringPreferencesKey("mcp_device_id")
    }

    val enabled: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLED] ?: false }

    val port: Flow<Int> = dataStore.data.map { it[KEY_PORT] ?: DEFAULT_PORT }

    val clientsJson: Flow<String> = dataStore.data.map { it[KEY_CLIENTS_JSON] ?: "" }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_ENABLED] = enabled }
    }

    suspend fun setPort(port: Int) {
        dataStore.edit { it[KEY_PORT] = port }
    }

    suspend fun setClientsJson(json: String) {
        dataStore.edit { it[KEY_CLIENTS_JSON] = json }
    }

    suspend fun deviceId(): String {
        val existing = dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val generated = "pp_" + bytes.joinToString("") { "%02x".format(it) }
        dataStore.edit { it[KEY_DEVICE_ID] = generated }
        return generated
    }
}
