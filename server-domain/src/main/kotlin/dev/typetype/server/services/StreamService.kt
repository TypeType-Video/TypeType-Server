package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse

interface StreamService {
    suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse>
}
