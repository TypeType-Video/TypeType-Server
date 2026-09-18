package dev.typetype.server.services

import dev.typetype.server.REQUEST_ID_HEADER
import dev.typetype.server.cache.CacheJson
import dev.typetype.server.currentRequestId
import dev.typetype.server.models.SubtitleItem
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

class YouTubeSubtitleService(private val httpClient: OkHttpClient, private val baseUrl: String) {

    suspend fun fetchSubtitles(videoId: String): List<SubtitleItem> =
        when (val result = fetchSubtitleInventory(videoId)) {
            is YouTubeSubtitleInventoryResult.Ready -> result.tracks
            YouTubeSubtitleInventoryResult.Unavailable -> emptyList()
        }

    suspend fun fetchSubtitleInventory(videoId: String): YouTubeSubtitleInventoryResult {
        val requestId = currentRequestId()
        return suspendCancellableCoroutine { continuation ->
            val request = Request.Builder()
                .url("$baseUrl/subtitles?videoId=$videoId")
                .apply { requestId?.let { header(REQUEST_ID_HEADER, it) } }
                .build()
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(YouTubeSubtitleInventoryResult.Unavailable)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        if (!it.isSuccessful) {
                            YouTubeSubtitleInventoryResult.Unavailable
                        } else {
                            runCatching<YouTubeSubtitleInventoryResult> {
                                YouTubeSubtitleInventoryResult.Ready(
                                    CacheJson.decodeFromString<List<SubtitleItem>>(it.body.string()),
                                )
                            }.getOrDefault(YouTubeSubtitleInventoryResult.Unavailable)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

}

sealed interface YouTubeSubtitleInventoryResult {
    data class Ready(val tracks: List<SubtitleItem>) : YouTubeSubtitleInventoryResult
    data object Unavailable : YouTubeSubtitleInventoryResult
}
