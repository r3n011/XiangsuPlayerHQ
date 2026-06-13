package com.theveloper.pixelplay.data

import android.app.Application
import com.google.android.gms.wearable.Wearable
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearBrowseResponse
import com.theveloper.pixelplay.shared.WearDataPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that manages library browse requests from the watch to the phone.
 *
 * Uses a request/response pattern via MessageClient:
 * 1. Watch sends WearBrowseRequest to phone
 * 2. Phone processes it (queries MusicRepository) and sends WearBrowseResponse back
 * 3. This repository correlates request/response via requestId using CompletableDeferred
 * 4. Responses are cached with a TTL to avoid redundant requests
 */
@Singleton
class WearLibraryRepository @Inject constructor(
    private val application: Application,
    private val stateRepository: WearStateRepository,
) {
    private val messageClient by lazy { Wearable.getMessageClient(application) }
    private val nodeClient by lazy { Wearable.getNodeClient(application) }
    private val json = Json { ignoreUnknownKeys = true }

    /** Pending requests awaiting a response: requestId -> CompletableDeferred */
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<WearBrowseResponse>>()

    /** Simple cache: cacheKey -> (timestamp, response) */
    private val cache = ConcurrentHashMap<String, Pair<Long, WearBrowseResponse>>()

    companion object {
        private const val TAG = "WearLibraryRepo"
        private const val CACHE_TTL_MS = 120_000L  // 2 minutes
        private const val REQUEST_TIMEOUT_MS = 10_000L  // 10 seconds
    }

    /**
     * Browse the phone's music library.
     * Returns cached data if available; otherwise sends a request to the phone and waits.
     *
     * @param browseType One of [WearBrowseRequest] constants (ALBUMS, ARTISTS, etc.)
     * @param contextId Optional context for sub-navigation (albumId, artistId, playlistId)
     * @return [WearBrowseResponse] with items or error
     */
    suspend fun browse(browseType: String, contextId: String? = null): WearBrowseResponse {
        val cacheKey = "$browseType:${contextId.orEmpty()}"
        val useCache = browseType != WearBrowseRequest.QUEUE

        if (useCache) {
            // Check cache first
            cache[cacheKey]?.let { (timestamp, response) ->
                if (System.currentTimeMillis() - timestamp < CACHE_TTL_MS) {
                    Timber.tag(TAG).d("Cache hit for $cacheKey (${response.items.size} items)")
                    return response
                } else {
                    cache.remove(cacheKey)
                }
            }
        }

        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<WearBrowseResponse>()
        pendingRequests[requestId] = deferred

        try {
            // Send request to phone
            val request = WearBrowseRequest(requestId, browseType, contextId)
            val requestBytes = json.encodeToString(request).toByteArray(Charsets.UTF_8)

            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) {
                stateRepository.setPhoneConnected(false)
                pendingRequests.remove(requestId)
                return WearBrowseResponse(
                    requestId = requestId,
                    error = "Phone not connected"
                )
            }
            stateRepository.setPhoneConnected(true)

            // Send to all connected nodes (typically just one phone)
            nodes.forEach { node ->
                try {
                    messageClient.sendMessage(node.id, WearDataPaths.BROWSE_REQUEST, requestBytes).await()
                    Timber.tag(TAG).d("Sent browse request to ${node.displayName}: $browseType")
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to send browse request to ${node.displayName}")
                }
            }

            // Wait for response with timeout
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                deferred.await()
            }

            // Cache successful responses
            if (useCache && response.error == null) {
                cache[cacheKey] = System.currentTimeMillis() to response
            }

            return response
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Browse request failed for $browseType")
            return WearBrowseResponse(
                requestId = requestId,
                error = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    "Request timed out"
                } else {
                    e.message ?: "Unknown error"
                }
            )
        } finally {
            pendingRequests.remove(requestId)
        }
    }

    /**
     * Called by [WearDataListenerService] when a browse response arrives from the phone.
     * Resolves the corresponding CompletableDeferred.
     */
    fun onBrowseResponseReceived(response: WearBrowseResponse) {
        val deferred = pendingRequests[response.requestId]
        if (deferred != null) {
            deferred.complete(response)
            Timber.tag(TAG).d(
                "Resolved browse response: ${response.items.size} items for requestId=${response.requestId}"
            )
        } else {
            Timber.tag(TAG).w("No pending request for responseId=${response.requestId}")
        }
    }

    /**
     * Invalidate all cached browse data. Call this when the library might have changed.
     */
    fun invalidateCache() {
        cache.clear()
        Timber.tag(TAG).d("Cache invalidated")
    }
}
