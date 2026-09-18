package dev.typetype.server.services

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.StreamResponse

data class AudioOnlyStreamSelection(
    val response: StreamResponse,
    val stream: AudioStreamItem,
    val kind: AudioOnlyStreamKind,
)
