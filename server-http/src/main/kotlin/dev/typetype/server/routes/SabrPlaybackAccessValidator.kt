package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_CODE
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_ERROR
import dev.typetype.server.services.requiresYoutubeSession

internal class SabrPlaybackAccessValidator(
    private val publicStreamService: StreamService,
    private val youtubeSessionStreamInfo: (suspend (String, String) -> ExtractionResult<StreamResponse>?)?,
) {
    suspend fun resolve(userId: String?, videoId: String): ExtractionResult<StreamResponse> {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val publicResult = publicStreamService.getStreamInfo(url)
        val authenticatedResult = userId?.let { id -> youtubeSessionStreamInfo?.invoke(id, url) }
        if (authenticatedResult != null) return authenticatedResult
        return if (publicResult.requiresYoutubeSession() && youtubeSessionStreamInfo != null) {
            ExtractionResult.BadRequest(YOUTUBE_SESSION_REQUIRED_ERROR, YOUTUBE_SESSION_REQUIRED_CODE)
        } else {
            publicResult
        }
    }
}
