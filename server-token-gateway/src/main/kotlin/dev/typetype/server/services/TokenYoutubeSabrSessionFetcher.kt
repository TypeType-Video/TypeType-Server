package dev.typetype.server.services

import dev.typetype.server.PLAYBACK_TRACE_ID_HEADER
import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.REQUEST_ID_HEADER
import dev.typetype.server.currentPlaybackTraceId
import dev.typetype.server.currentRequestId
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

internal class TokenYoutubeSabrSessionFetcher(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient,
) {
    suspend fun fetch(videoId: String, isolated: Boolean): TokenServiceResult<JSONObject> {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        val url = "${tokenServiceUrl.trimEnd('/')}/youtube/sabr/session?videoId=$encodedVideoId&client=MWEB" +
            if (isolated) "&isolated=true" else ""
        return suspendCancellableCoroutine { continuation ->
            val requestBuilder = Request.Builder().url(url).get()
            currentRequestId()?.let { requestBuilder.header(REQUEST_ID_HEADER, it) }
            currentPlaybackTraceId()?.let { requestBuilder.header(PLAYBACK_TRACE_ID_HEADER, it) }
            val traceId = currentPlaybackTraceId()
            val requestId = currentRequestId()
            val startedAt = System.nanoTime()
            PlaybackTraceLog.record("token_session_start", "operation=youtube_sabr_session isolated=$isolated")
            val call = client.newCall(requestBuilder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    PlaybackTraceLog.record(traceId, requestId, "token_session_complete", "status=network_error durationMs=${elapsedMs(startedAt)}")
                    if (continuation.isActive) continuation.resume(TokenServiceResponses.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = response.use { TokenServiceResponses.read(it) { json -> json } }
                    val outcome = if (result is TokenServiceResult.Success) "ok" else "empty"
                    PlaybackTraceLog.record(traceId, requestId, "token_session_complete", "status=${response.code} result=$outcome durationMs=${elapsedMs(startedAt)}")
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    private fun elapsedMs(startedAt: Long): Long = (System.nanoTime() - startedAt) / 1_000_000
}
