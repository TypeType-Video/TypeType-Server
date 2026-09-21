package dev.typetype.server.services

import dev.typetype.server.models.VideoStreamItem

fun isDashManifestVideoStream(stream: VideoStreamItem): Boolean {
    val codec = stream.codec ?: return false
    val height = stream.height.takeIf { it > 0 } ?: dashResolutionHeight(stream.resolution)
    return stream.url.isNotBlank() && codec.startsWith("avc1") && (height <= 0 || height <= 1080)
}

private fun dashResolutionHeight(resolution: String): Int =
    Regex("(\\d+)[pP]").find(resolution)?.groupValues?.get(1)?.toIntOrNull() ?: 0
