package dev.typetype.server.routes

import io.ktor.http.ContentType

internal fun containerMime(mime: String): ContentType {
    val container = mime.substringBefore(";").trim().lowercase()
    return when (container) {
        "video/webm" -> ContentType("video", "webm")
        "video/mp4" -> ContentType.Video.MP4
        "audio/webm" -> ContentType("audio", "webm")
        "audio/mp4" -> ContentType("audio", "mp4")
        "audio/ogg", "audio/opus" -> ContentType("audio", "ogg")
        else -> ContentType.Application.OctetStream
    }
}
