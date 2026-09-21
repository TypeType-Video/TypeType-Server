package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.AudioOnlyMediaToken
import dev.typetype.server.services.AudioOnlyStreamSelection

internal class AudioOnlySourceResolution(
    val token: AudioOnlyMediaToken,
    val result: ExtractionResult<AudioOnlyStreamSelection>,
)
