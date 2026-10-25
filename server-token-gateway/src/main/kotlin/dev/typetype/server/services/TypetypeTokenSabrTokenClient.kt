package dev.typetype.server.services

import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import dev.typetype.server.PLAYBACK_TRACE_ID_HEADER
import dev.typetype.server.REQUEST_ID_HEADER
import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.currentPlaybackTraceId
import dev.typetype.server.currentRequestId
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class TypetypeTokenSabrTokenClient(
    private val tokenServiceUrl: String,
    client: OkHttpClient = OkHttpClient(),
    val sessionEpoch: ProviderSessionEpoch = ProviderSessionEpoch(),
) {
    internal val client: OkHttpClient = TypetypeTokenHttpClient.bounded(client)

    fun fetch(videoId: String, forceRefresh: Boolean = false, refreshVideo: Boolean = false): SabrTokenBundle? =
        fetchResult(videoId, forceRefresh, refreshVideo).valueOrNull()

    fun fetchResult(
        videoId: String,
        forceRefresh: Boolean = false,
        refreshVideo: Boolean = false,
    ): TokenServiceResult<SabrTokenBundle> =
        fetch(videoId, forceRefresh, refreshVideo, logIdentifier = true).also { result ->
            result.valueOrNull()?.let { sessionEpoch.observe(it, renewed = forceRefresh) }
        }

    // The Token minter accepts an opaque binding even though its internal query parameter is named videoId.
    fun fetchBoundToken(binding: String): String? =
        fetch(binding, forceRefresh = false, refreshVideo = false, logIdentifier = false).valueOrNull()?.videoBoundPoToken

    fun fetchSession(
        videoId: String,
        sessionBinding: String,
        refreshVideo: Boolean = false,
    ): SabrTokenBundle? {
        val body = JSONObject()
            .put("videoId", videoId)
            .put("sessionBinding", sessionBinding)
            .put("refreshVideo", refreshVideo)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("${tokenServiceUrl.trimEnd('/')}/potoken/session")
            .post(body)
            .apply {
                currentRequestId()?.let { header(REQUEST_ID_HEADER, it) }
                currentPlaybackTraceId()?.let { header(PLAYBACK_TRACE_ID_HEADER, it) }
            }
            .build()
        val startedAt = System.nanoTime()
        return try {
            client.newCall(request).execute().use { response ->
                PlaybackTraceLog.record("token_http", "endpoint=potoken_session status=${response.code} durationMs=${(System.nanoTime() - startedAt) / 1_000_000}")
                if (!response.isSuccessful) return null
                SabrTokenBundle.fromSessionResponse(videoId, sessionBinding, JSONObject(response.body.string()))
            }
        } catch (error: Exception) {
            System.err.println(
                "[TypetypeTokenSabrTokenClient] authenticated token fetch failed: ${error.message}",
            )
            null
        }
    }

    private fun fetch(
        binding: String,
        forceRefresh: Boolean,
        refreshVideo: Boolean,
        logIdentifier: Boolean,
    ): TokenServiceResult<SabrTokenBundle> {
        val url = buildUrl(binding, forceRefresh, refreshVideo)
        val target = if (logIdentifier) " for $binding" else " for session binding"
        val startedAt = System.nanoTime()
        val request = Request.Builder().url(url).get().apply {
            currentRequestId()?.let { header(REQUEST_ID_HEADER, it) }
            currentPlaybackTraceId()?.let { header(PLAYBACK_TRACE_ID_HEADER, it) }
        }.build()
        val result = try {
            client.newCall(request).execute().use { resp ->
                PlaybackTraceLog.record("token_http", "endpoint=potoken status=${resp.code} durationMs=${(System.nanoTime() - startedAt) / 1_000_000}")
                TokenServiceResponses.read(resp) { SabrTokenBundle.fromResponse(binding, it) }
            }
        } catch (error: IOException) {
            TokenServiceResponses.failure(error)
        }
        if (result is TokenServiceResult.Failure) {
            System.err.println("[TypetypeTokenSabrTokenClient] /potoken ${result.reason} status=${result.status}$target: ${result.error}")
        }
        return result
    }

    private fun buildUrl(videoId: String, forceRefresh: Boolean, refreshVideo: Boolean): String {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        return "${tokenServiceUrl.trimEnd('/')}/potoken?videoId=$encodedVideoId" +
            (if (forceRefresh) "&refresh=true" else "") +
            (if (refreshVideo) "&refreshVideo=true" else "")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
