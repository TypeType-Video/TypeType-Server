package dev.typetype.server.services

import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.util.Base64

internal class SabrTokenBundle(
    val videoId: String,
    val visitorBoundPoToken: String,
    val visitorBoundPoTokenBytes: ByteArray,
    val visitorData: String,
    val videoBoundPoToken: String,
    val videoBoundPoTokenBytes: ByteArray,
    val sessionBinding: String? = null,
    val sessionBoundPoToken: String? = null,
) {
    val visitorPoToken: String = visitorBoundPoToken
    val visitorPoTokenBytes: ByteArray = visitorBoundPoTokenBytes
    val streamingPoToken: String = videoBoundPoToken
    val streamingPoTokenBytes: ByteArray = videoBoundPoTokenBytes

    companion object {
        fun fromResponse(videoId: String, json: JSONObject): SabrTokenBundle? = runCatching {
            val visitorBoundPoToken = json.optString("visitorBoundPoToken").ifBlank { json.optString("poToken") }
            val visitorData = json.optString("visitorData")
            val videoBoundPoToken = json.optString("videoBoundPoToken").ifBlank { json.optString("streamingPot") }
            if (visitorBoundPoToken.isBlank() || visitorData.isBlank() || videoBoundPoToken.isBlank()) return null
            SabrTokenBundle(
                videoId = videoId,
                visitorBoundPoToken = visitorBoundPoToken,
                visitorBoundPoTokenBytes = decodeBase64Url(visitorBoundPoToken),
                visitorData = visitorData,
                videoBoundPoToken = videoBoundPoToken,
                videoBoundPoTokenBytes = decodeBase64Url(videoBoundPoToken),
            )
        }.getOrNull()

        fun fromSessionResponse(
            videoId: String,
            sessionBinding: String,
            json: JSONObject,
        ): SabrTokenBundle? {
            val base = fromResponse(videoId, json) ?: return null
            val sessionBoundPoToken = json.optString("sessionBoundPoToken").takeIf(String::isNotBlank)
                ?: return null
            return SabrTokenBundle(
                videoId = base.videoId,
                visitorBoundPoToken = base.visitorBoundPoToken,
                visitorBoundPoTokenBytes = base.visitorBoundPoTokenBytes,
                visitorData = base.visitorData,
                videoBoundPoToken = base.videoBoundPoToken,
                videoBoundPoTokenBytes = base.videoBoundPoTokenBytes,
                sessionBinding = sessionBinding,
                sessionBoundPoToken = sessionBoundPoToken,
            )
        }

        private fun decodeBase64Url(value: String): ByteArray {
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            return Base64.getUrlDecoder().decode(padded)
        }
    }
}

internal fun SabrTokenBundle.youtubeSessionPoToken(): YoutubeSessionPoToken =
    YoutubeSessionPoToken(sessionBinding ?: visitorData, sessionBoundPoToken ?: visitorBoundPoToken)

internal fun SabrTokenBundle.streamingPoTokenBytesFor(info: YoutubeSabrInfo): ByteArray? =
    takeIf {
        it.videoId == info.videoId &&
            (it.visitorData == info.visitorData || it.sessionBinding == info.visitorData)
    }
        ?.streamingPoTokenBytes
        ?.takeIf { it.isNotEmpty() }

internal const val SABR_TOKEN_BINDING_FAILURE = "SABR token does not match session visitorData"
