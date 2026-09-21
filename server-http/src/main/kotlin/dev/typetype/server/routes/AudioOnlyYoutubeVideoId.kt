package dev.typetype.server.routes

internal fun String.youtubeVideoId(): String? = Regex("(?:[?&]v=|/shorts/|youtu\\.be/)([A-Za-z0-9_-]{6,})")
    .find(this)
    ?.groupValues
    ?.getOrNull(1)
