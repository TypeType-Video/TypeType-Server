package dev.typetype.server.services

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.schabi.newpipe.extractor.MediaFormat

enum class YouTubeSubtitleFormat(
    val value: String,
    val mediaFormat: MediaFormat,
    val contentType: String,
) {
    Vtt("vtt", MediaFormat.VTT, "text/vtt; charset=utf-8"),
    Ttml("ttml", MediaFormat.TTML, "application/ttml+xml; charset=utf-8");

    companion object {
        fun from(value: String?): YouTubeSubtitleFormat? = entries.firstOrNull { it.value == value }
    }
}

enum class YouTubeSubtitleVariant(val value: String) {
    Manual("manual"),
    Auto("auto");

    companion object {
        fun from(value: String?): YouTubeSubtitleVariant? = entries.firstOrNull { it.value == value }
    }
}

data class YouTubeSubtitleSelection(
    val videoId: String,
    val language: String,
    val variant: YouTubeSubtitleVariant,
    val format: YouTubeSubtitleFormat,
    val sourceLanguage: String? = null,
    val translationLanguage: String? = null,
    val trackName: String? = null,
) {
    val cacheKey: String = listOf(
        videoId,
        language.lowercase(),
        variant.value,
        format.value,
        sourceLanguage.orEmpty().lowercase(),
        translationLanguage.orEmpty().lowercase(),
        trackName.orEmpty(),
    ).joinToString(":")
}

data class ResolvedYouTubeSubtitle(
    val content: String,
    val isUrl: Boolean,
    val isLive: Boolean,
)

sealed interface YouTubeSubtitleResolution {
    data class Ready(val track: ResolvedYouTubeSubtitle) : YouTubeSubtitleResolution
    data object NotFound : YouTubeSubtitleResolution
    data object Throttled : YouTubeSubtitleResolution
    data object Unavailable : YouTubeSubtitleResolution
}

sealed interface YouTubeSubtitleFetchResult {
    data class Ready(val content: ByteArray) : YouTubeSubtitleFetchResult
    data object Expired : YouTubeSubtitleFetchResult
    data object Throttled : YouTubeSubtitleFetchResult
    data object InvalidPayload : YouTubeSubtitleFetchResult
    data object Unavailable : YouTubeSubtitleFetchResult
}

sealed interface YouTubeSubtitleContentResult {
    data class Ready(
        val content: ByteArray,
        val format: YouTubeSubtitleFormat,
        val isLive: Boolean,
    ) : YouTubeSubtitleContentResult

    data object InvalidRequest : YouTubeSubtitleContentResult
    data object NotFound : YouTubeSubtitleContentResult
    data object Throttled : YouTubeSubtitleContentResult
    data object Expired : YouTubeSubtitleContentResult
    data object InvalidPayload : YouTubeSubtitleContentResult
    data object Unavailable : YouTubeSubtitleContentResult
}

fun subtitleSelectionFromTimedTextUrl(rawUrl: String): YouTubeSubtitleSelection? {
    val url = rawUrl.toHttpUrlOrNull()?.takeIf { it.isYouTubeTimedText() } ?: return null
    val videoId = url.queryParameter("v")?.trim().orEmpty()
    val sourceLanguage = url.queryParameter("lang")?.trim().orEmpty()
    val translation = url.queryParameter("tlang")?.trim()?.takeIf(String::isNotEmpty)
    if (!isValidYouTubeVideoId(videoId) || !isValidSubtitleTag(sourceLanguage)) return null
    return YouTubeSubtitleSelection(
        videoId = videoId,
        language = translation ?: sourceLanguage,
        variant = if (url.queryParameter("kind") == "asr" ||
            url.queryParameter("vssId")?.startsWith("a.") == true
        ) YouTubeSubtitleVariant.Auto else YouTubeSubtitleVariant.Manual,
        format = YouTubeSubtitleFormat.Vtt,
        sourceLanguage = sourceLanguage,
        translationLanguage = translation,
        trackName = url.queryParameter("name")?.trim()?.takeIf(String::isNotEmpty),
    )
}

fun isYouTubeTimedTextUrl(rawUrl: String): Boolean =
    rawUrl.toHttpUrlOrNull()?.isYouTubeTimedText() == true

fun isValidYouTubeVideoId(value: String): Boolean =
    value.length == 11 && value.all { it.isLetterOrDigit() || it == '_' || it == '-' }

fun isValidSubtitleTag(value: String): Boolean =
    value.isNotEmpty() && value.length <= 64 && value.all { it.isLetterOrDigit() || it in "-_." }

private fun okhttp3.HttpUrl.isYouTubeTimedText(): Boolean =
    isHttps && (host == "youtube.com" || host.endsWith(".youtube.com")) && encodedPath == "/api/timedtext"
