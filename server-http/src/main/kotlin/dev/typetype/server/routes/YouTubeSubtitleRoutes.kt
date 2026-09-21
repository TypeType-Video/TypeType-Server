package dev.typetype.server.routes

import dev.typetype.server.services.YouTubeSubtitleDeliveryService
import dev.typetype.server.services.YouTubeSubtitleFormat
import dev.typetype.server.services.YouTubeSubtitleSelection
import dev.typetype.server.services.YouTubeSubtitleVariant
import dev.typetype.server.services.isValidSubtitleTag
import dev.typetype.server.services.isValidYouTubeVideoId
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

internal fun Route.youtubeSubtitleRoutes(service: YouTubeSubtitleDeliveryService) {
    get("/subtitles/youtube/{videoId}") {
        val selection = call.subtitleSelection()
            ?: return@get call.respondYouTubeSubtitleInvalidRequest()
        call.respondYouTubeSubtitle(service.fetch(selection))
    }
}

private fun io.ktor.server.application.ApplicationCall.subtitleSelection(): YouTubeSubtitleSelection? {
    val videoId = parameters["videoId"]?.trim().orEmpty()
    val language = request.queryParameters["language"]?.trim().orEmpty()
    val variant = YouTubeSubtitleVariant.from(request.queryParameters["variant"])
    val format = YouTubeSubtitleFormat.from(request.queryParameters["format"] ?: "vtt")
    val sourceLanguage = request.queryParameters["sourceLanguage"]?.trim()?.takeIf(String::isNotEmpty)
    val translation = request.queryParameters["translation"]?.trim()?.takeIf(String::isNotEmpty)
    val trackName = request.queryParameters["name"]?.trim()?.takeIf(String::isNotEmpty)
    if (!isValidYouTubeVideoId(videoId) || !isValidSubtitleTag(language)) return null
    if (variant == null || format == null) return null
    if (sourceLanguage != null && !isValidSubtitleTag(sourceLanguage)) return null
    if (translation != null && !isValidSubtitleTag(translation)) return null
    if (trackName != null && trackName.length > MAX_TRACK_NAME_LENGTH) return null
    return YouTubeSubtitleSelection(
        videoId = videoId,
        language = language,
        variant = variant,
        format = format,
        sourceLanguage = sourceLanguage,
        translationLanguage = translation,
        trackName = trackName,
    )
}

private const val MAX_TRACK_NAME_LENGTH = 128
