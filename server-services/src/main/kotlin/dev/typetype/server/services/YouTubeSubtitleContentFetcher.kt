package dev.typetype.server.services

import dev.typetype.server.REQUEST_ID_HEADER
import dev.typetype.server.currentRequestId
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume

fun interface YouTubeSubtitleContentFetcher {
    suspend fun fetch(url: String, format: YouTubeSubtitleFormat): YouTubeSubtitleFetchResult
}

class OkHttpYouTubeSubtitleContentFetcher(
    private val client: OkHttpClient,
) : YouTubeSubtitleContentFetcher {
    override suspend fun fetch(url: String, format: YouTubeSubtitleFormat): YouTubeSubtitleFetchResult {
        if (!isYouTubeTimedTextUrl(url)) return YouTubeSubtitleFetchResult.Unavailable
        val request = Request.Builder()
            .url(url)
            .header("Accept", "${format.contentType},*/*;q=0.8")
            .header("Origin", YOUTUBE_ORIGIN)
            .header("Referer", "$YOUTUBE_ORIGIN/")
            .header("User-Agent", OkHttpProxyService.BROWSER_USER_AGENT)
            .apply { currentRequestId()?.let { header(REQUEST_ID_HEADER, it) } }
            .build()
        return client.executeSubtitleRequest(request, format)
    }
}

class TokenYouTubeSubtitleContentFetcher(
    private val client: OkHttpClient,
    baseUrl: String,
    private val directFetcher: YouTubeSubtitleContentFetcher,
) : YouTubeSubtitleContentFetcher {
    private val endpoint = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments("subtitles/content")
        .build()

    override suspend fun fetch(url: String, format: YouTubeSubtitleFormat): YouTubeSubtitleFetchResult {
        if (format == YouTubeSubtitleFormat.Ttml) return directFetcher.fetch(url, format)
        if (!isYouTubeTimedTextUrl(url)) return YouTubeSubtitleFetchResult.Unavailable
        val request = Request.Builder()
            .url(endpoint.newBuilder().addQueryParameter("url", url).build())
            .header("Accept", format.contentType)
            .apply { currentRequestId()?.let { header(REQUEST_ID_HEADER, it) } }
            .build()
        return client.executeSubtitleRequest(request, format)
    }
}

private suspend fun OkHttpClient.executeSubtitleRequest(
    request: Request,
    format: YouTubeSubtitleFormat,
): YouTubeSubtitleFetchResult = suspendCancellableCoroutine { continuation ->
    val call = newCall(request)
    continuation.invokeOnCancellation { call.cancel() }
    call.enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resume(YouTubeSubtitleFetchResult.Unavailable)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching { response.use { readSubtitleResponse(it, format) } }
                .getOrDefault(YouTubeSubtitleFetchResult.Unavailable)
            if (continuation.isActive) continuation.resume(result)
        }
    })
}

private fun readSubtitleResponse(response: Response, format: YouTubeSubtitleFormat): YouTubeSubtitleFetchResult {
    if (response.code == 429) return YouTubeSubtitleFetchResult.Throttled
    if (response.code == 403 || response.code == 404 || response.code == 410) {
        return YouTubeSubtitleFetchResult.Expired
    }
    if (!response.isSuccessful) return YouTubeSubtitleFetchResult.Unavailable
    val body = response.body
    if (body.contentLength() > MAX_SUBTITLE_BYTES) return YouTubeSubtitleFetchResult.InvalidPayload
    return runCatching { body.byteStream().readNBytes(MAX_SUBTITLE_BYTES + 1) }
        .fold(
            onSuccess = { bytes ->
                if (isValidSubtitlePayload(bytes, format)) YouTubeSubtitleFetchResult.Ready(bytes)
                else YouTubeSubtitleFetchResult.InvalidPayload
            },
            onFailure = { YouTubeSubtitleFetchResult.Unavailable },
        )
}

private const val YOUTUBE_ORIGIN = "https://m.youtube.com"
const val MAX_SUBTITLE_BYTES = 5 * 1024 * 1024

fun isValidSubtitlePayload(content: ByteArray, format: YouTubeSubtitleFormat): Boolean {
    if (content.isEmpty() || content.size > MAX_SUBTITLE_BYTES) return false
    val text = content.toString(Charsets.UTF_8).trimStart()
    return when (format) {
        YouTubeSubtitleFormat.Vtt -> text.startsWith("WEBVTT")
        YouTubeSubtitleFormat.Ttml -> text.startsWith("<?xml") || text.startsWith("<tt")
    }
}
