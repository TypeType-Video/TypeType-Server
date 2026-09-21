package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.StreamResponse

const val YOUTUBE_SESSION_RECONNECT_ERROR = "YouTube session needs reconnect"

class YoutubeSessionStreamService(
    private val streamService: StreamService,
    private val youtubeSessionService: YoutubeSessionService,
    private val cache: CacheService,
    private val tokenService: SignedHlsManifestTokenService,
) {
    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        if (!isYoutubeUrl(url)) return null
        val credentials = youtubeSessionService.connectedCredentials(userId) ?: return null
        val result = getStreamInfoForCredentials(credentials, url)
        return when (result) {
            is ExtractionResult.Success ->
                ExtractionResult.Success(result.data.withSignedHlsUrl(credentials, url))
            is ExtractionResult.BadRequest -> result
            is ExtractionResult.Failure -> result
        }
    }

    suspend fun getStreamInfoForCredentials(
        credentials: YoutubeSessionCredentials,
        url: String,
    ): ExtractionResult<StreamResponse> {
        val result = authenticatedCache(credentials, url)
        if (result is ExtractionResult.Success) {
            youtubeSessionService.markUsed(credentials.userId)
            return result
        }
        if (requiresReconnect(result)) {
            youtubeSessionService.markNeedsReconnect(credentials.userId)
            return ExtractionResult.BadRequest(
                YOUTUBE_SESSION_RECONNECT_ERROR,
                YOUTUBE_SESSION_RECONNECT_CODE,
            )
        }
        youtubeSessionService.markUsed(credentials.userId)
        return result
    }

    private fun StreamResponse.withSignedHlsUrl(
        credentials: YoutubeSessionCredentials,
        url: String,
    ): StreamResponse =
        if (hlsUrl.isBlank()) {
            this
        } else {
            copy(hlsUrl = tokenService.createPath(credentials.userId, url, credentials.fingerprint))
        }

    private suspend fun authenticatedCache(
        credentials: YoutubeSessionCredentials,
        url: String,
    ): ExtractionResult<StreamResponse> = PublicExtractionCache.getOrLoad(
        cache = cache,
        area = "stream-auth-v4",
        key = PublicCacheKey.of("stream-auth-v4", credentials.userId, credentials.fingerprint, url),
        serializer = StreamResponse.serializer(),
        ttlSeconds = { minOf(it.streamCacheTtlSeconds(), AUTHENTICATED_STREAM_MAX_TTL_SECONDS) },
    ) {
        YoutubeSessionTokenScope.withCredentials(credentials) {
            streamService.getStreamInfo(url)
        }
    }

    private fun requiresReconnect(result: ExtractionResult<StreamResponse>): Boolean =
        result is ExtractionResult.Failure &&
            result.kind == ExtractionFailureKind.YoutubeSessionRejected

    private companion object {
        const val AUTHENTICATED_STREAM_MAX_TTL_SECONDS = 900L
    }
}

const val YOUTUBE_SESSION_RECONNECT_CODE = "youtube_session_needs_reconnect"
