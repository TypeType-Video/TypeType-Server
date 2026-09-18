package dev.typetype.server.routes

import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.ProxyService

internal suspend fun ProxyService.isPlayableProgressiveAudio(stream: AudioStreamItem): Boolean =
    when (val result = pipe(stream.url, audioOnlyProbeRangeHeader(), null).ensureProgressiveAudio()) {
        is ExtractionResult.Success -> {
            result.data.close()
            true
        }
        is ExtractionResult.BadRequest -> false
        is ExtractionResult.Failure -> false
    }
