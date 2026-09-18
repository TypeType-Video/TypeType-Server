package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.VideoStreamItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.VideoStream

fun VideoStreamItem.manifestRepresentationId(index: Int): String = "v-${itag.takeIf { it > 0 } ?: index}"

fun AudioStreamItem.manifestRepresentationId(index: Int): String =
    stableAudioRepresentationId(itag = itag, trackId = audioTrackId, index = index)

fun VideoStream.manifestRepresentationId(index: Int): String = "v-${getItag().takeIf { it > 0 } ?: index}"

fun AudioStream.manifestRepresentationId(index: Int): String =
    stableAudioRepresentationId(itag = getItag(), trackId = getAudioTrackId(), index = index)

private fun stableAudioRepresentationId(itag: Int, trackId: String?, index: Int): String {
    val base = "a-${itag.takeIf { it > 0 } ?: index}"
    val suffix = trackId?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }?.takeIf { it.isNotBlank() }
    return suffix?.let { "$base-$it" } ?: base
}
