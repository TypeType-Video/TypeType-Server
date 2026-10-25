package dev.typetype.server.services

import com.grack.nanojson.JsonParser
import dev.typetype.server.sabr.SabrAdapter
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrInfo
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

internal fun JSONObject.toPlaybackSession(videoId: String): TokenYoutubeSession? {
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

internal fun JSONObject.toSabrInfo(videoId: String): YoutubeSabrInfo? = runCatching {
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
