package dev.typetype.server.services

import dev.typetype.server.sabr.YoutubeSabrFormat

internal fun splitMime(mime: String): Pair<String, String> {
    val parts = mime.split(";", limit = 2)
    val container = parts[0].trim()
    val codecs = if (parts.size > 1) {
        parts[1].trim().removePrefix("codecs=\"").removeSuffix("\"")
    } else {
        ""
    }
    return container to codecs
}

internal fun videoSizeAttr(video: YoutubeSabrFormat): String {
    val width = video.width.takeIf { it > 0 }
    val height = video.height.takeIf { it > 0 }
    return if (width != null && height != null) " width=\"$width\" height=\"$height\"" else ""
}
