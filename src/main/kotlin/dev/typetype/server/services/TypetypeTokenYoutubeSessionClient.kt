package dev.typetype.server.services

import com.grack.nanojson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import dev.typetype.server.sabr.SabrAdapter
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

internal class TypetypeTokenYoutubeSessionClient(
    private val tokenServiceUrl: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchHlsManifestUrl(videoId: String): String? = fetchSession(videoId)
        ?.optString("hlsManifestUrl")
        ?.trim()
        ?.ifBlank { null }

    suspend fun fetchSabrInfo(videoId: String): YoutubeSabrInfo? = fetchSession(videoId)?.let { session ->
        session.toSabrInfo(videoId)
    }

    suspend fun fetchPlaybackSession(
        videoId: String,
        isolated: Boolean = false,
    ): TokenYoutubeSession? = fetchSession(videoId, isolated)?.let { session ->
        session.toPlaybackSession(videoId)
    }

    private fun JSONObject.toPlaybackSession(videoId: String): TokenYoutubeSession? {
        val session = this
        val info = session.toSabrInfo(videoId) ?: return null
        val metadata = session.optJSONObject("metadata") ?: JSONObject()
        return TokenYoutubeSession(
            info = info,
            token = SabrTokenBundle.fromResponse(videoId, session),
            title = metadata.optString("title", session.optString("title")),
            author = metadata.optString("author"),
            channelId = metadata.optString("channelId"),
            channelAvatarUrl = metadata.optString("channelAvatarUrl"),
            description = metadata.optString("description"),
            durationMs = metadata.optLong("durationMs", session.optLong("durationMs")),
            viewCount = metadata.optLong("viewCount"),
            thumbnailUrl = metadata.optString("thumbnailUrl"),
            tags = metadata.optJSONArray("tags")?.let { tags ->
                List(tags.length()) { index -> tags.optString(index) }.filter { it.isNotBlank() }
            }.orEmpty(),
            isLive = metadata.optBoolean("isLive"),
            isLiveContent = metadata.optBoolean("isLiveContent"),
            hlsUrl = session.optString("hlsManifestUrl").trim(),
        )
    }

    private suspend fun fetchSession(videoId: String, isolated: Boolean = false): JSONObject? {
        val encodedVideoId = URLEncoder.encode(videoId, StandardCharsets.UTF_8)
        val url = "${tokenServiceUrl.trimEnd('/')}/youtube/sabr/session?videoId=$encodedVideoId&client=MWEB" +
            if (isolated) "&isolated=true" else ""
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val session = response.use {
                        if (!it.isSuccessful) null else runCatching { JSONObject(it.body.string()) }.getOrNull()
                    }
                    if (continuation.isActive) continuation.resume(session)
                }
            })
        }
    }

    private fun JSONObject.toSabrInfo(videoId: String): YoutubeSabrInfo? = runCatching {
        val playerResponse = JSONObject()
            .put("responseContext", JSONObject().put("visitorData", getString("visitorData")))
            .put(
                "streamingData",
                JSONObject()
                    .put(
                        "serverAbrStreamingUrl",
                        optString("rawServerAbrStreamingUrl").ifBlank { getString("serverAbrStreamingUrl") },
                    )
                    .put("adaptiveFormats", getJSONArray("adaptiveFormats")),
            )
            .put(
                "playerConfig",
                JSONObject().put(
                    "mediaCommonConfig",
                    JSONObject().put(
                        "mediaUstreamerRequestConfig",
                        JSONObject().put("videoPlaybackUstreamerConfig", getString("videoPlaybackUstreamerConfig")),
                    ),
                ),
            )
        val info = SabrAdapter.fromPlayerResponse(
            videoId,
            YoutubeSabrClientProfile.MWEB,
            YoutubeParsingHelper.generateContentPlaybackNonce(),
            JsonParser.`object`().from(playerResponse.toString()),
        )
        val playbackUrl = optString("serverAbrStreamingUrl").takeIf { it.isNotBlank() }
        val parsedPlaybackUrl = playbackUrl?.toHttpUrlOrNull()
        val clientVersion = parsedPlaybackUrl
            ?.queryParameter("cver")
            ?.takeIf { it.isNotBlank() }
        val playbackCpn = parsedPlaybackUrl
            ?.queryParameter("cpn")
            ?.takeIf { it.isNotBlank() }
        if (playbackUrl != null && clientVersion != null) {
            SabrAdapter.withPlaybackIdentity(
                info,
                playbackUrl,
                clientVersion,
                playbackCpn ?: info.cpn,
                getString("visitorData"),
            )
        } else {
            info
        }
    }.getOrNull()

}
