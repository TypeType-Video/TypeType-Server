package dev.typetype.server.services

import dev.typetype.server.models.EndScreenItem
import org.schabi.newpipe.extractor.stream.EndScreenElement

private val SUPPORTED_TYPES = setOf("VIDEO", "PLAYLIST", "CHANNEL", "WEBSITE")

/** Keeps the elements a client can place and open: known type, a destination, timing and geometry. */
fun List<EndScreenElement>.toEndScreenItems(): List<EndScreenItem> = mapNotNull { element ->
    val placed = element.startTimeMs >= 0 && element.endTimeMs > element.startTimeMs &&
        element.left in 0.0..1.0 && element.top in 0.0..1.0 &&
        element.width > 0.0 && element.width <= 1.0 && element.aspectRatio > 0.0
    if (!placed || element.type !in SUPPORTED_TYPES || !element.url.startsWith("https://")) return@mapNotNull null
    EndScreenItem(
        id = element.id,
        type = element.type.lowercase(),
        title = element.title.orEmpty(),
        url = element.url,
        thumbnailUrl = element.thumbnails.maxByOrNull { it.width }?.url.orEmpty(),
        startMs = element.startTimeMs,
        endMs = element.endTimeMs,
        left = element.left,
        top = element.top,
        width = element.width,
        aspectRatio = element.aspectRatio,
    )
}
