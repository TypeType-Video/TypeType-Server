package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionFailureKind
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

const val YOUTUBE_SESSION_REQUIRED_CODE = "youtube_session_required"
const val YOUTUBE_SESSION_REQUIRED_ERROR = "Connect YouTube to access this video"

class YoutubeSessionSabrStreamService(
    private val metadataService: YoutubeSessionStreamService,
    private val infoService: AuthenticatedSabrInfoService,
    private val cache: CacheService,
    private val timeoutMs: Long = AuthenticatedSabrPolicy.STREAM_TIMEOUT_MS,
) {
    private val inFlight =
        ConcurrentHashMap<String, CompletableDeferred<ExtractionResult<StreamResponse>?>>()

    suspend fun getStreamInfo(userId: String, url: String): ExtractionResult<StreamResponse>? {
        val videoId = youtubeVideoId(url)
            ?: return ExtractionResult.BadRequest("Invalid YouTube URL")
        val key = PublicCacheKey.of(AUTHENTICATED_STREAM_CACHE_AREA, userId, videoId)
        cachedStream(key)?.let { return it }

        val pending = CompletableDeferred<ExtractionResult<StreamResponse>?>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return awaitShared(userId, url, existing)
        return try {
            cachedStream(key)?.let {
                pending.complete(it)
                return it
            }
            val result = loadStreamInfo(userId, url, videoId)
            if (result is ExtractionResult.Success) cacheStream(key, result.data)
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private suspend fun awaitShared(
        userId: String,
        url: String,
        shared: CompletableDeferred<ExtractionResult<StreamResponse>?>,
    ): ExtractionResult<StreamResponse>? = try {
        shared.await()
    } catch (_: CancellationException) {
        currentCoroutineContext().ensureActive()
        getStreamInfo(userId, url)
    }

    private suspend fun loadStreamInfo(
        userId: String,
        url: String,
        videoId: String,
    ): ExtractionResult<StreamResponse>? = try {
        withTimeout(timeoutMs) {
            val metadata = metadataService.getStreamInfo(userId, url)
            if (metadata != null && metadata !is ExtractionResult.Success) {
                return@withTimeout metadata
            }
            val data = metadata?.data
            if (data?.isLive == true) return@withTimeout null
            infoService.oauthStream(userId, videoId)?.let { return@withTimeout it }
            if (data == null) return@withTimeout null
            when (val info = infoService.fetch(userId, videoId)) {
                is AuthenticatedSabrInfoResult.Ready ->
                    ExtractionResult.Success(data.withSabrFallback(videoId, info.prepared.info))
                AuthenticatedSabrInfoResult.Failed -> {
                    logger.warn("authenticated_sabr_stream event=sabr_failed_fallback_to_standard videoId={}", videoId)
                    ExtractionResult.Success(data)
                }
                AuthenticatedSabrInfoResult.TimedOut -> {
                    logger.warn("authenticated_sabr_stream event=sabr_timeout_fallback_to_standard videoId={}", videoId)
                    ExtractionResult.Success(data)
                }
                AuthenticatedSabrInfoResult.NotConnected -> null
            }
        }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        logger.warn("authenticated_sabr_stream event=overall_timeout_transient_failure")
        ExtractionResult.Failure(
            "Timed out loading authenticated YouTube stream",
            StreamExtractionErrorMapper.UPSTREAM_TIMEOUT_CODE,
            ExtractionFailureKind.UpstreamTimeout,
        )
    }

    private suspend fun cachedStream(key: String): ExtractionResult<StreamResponse>? {
        val value = try {
            cache.get(key)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("authenticated_sabr_stream_cache event=get_failed", error)
            return null
        } ?: return null
        return try {
            ExtractionResult.Success(CacheJson.decodeFromString<StreamResponse>(value))
        } catch (error: Exception) {
            logger.warn("authenticated_sabr_stream_cache event=decode_failed", error)
            null
        }
    }

    private suspend fun cacheStream(key: String, response: StreamResponse) {
        val ttlSeconds = minOf(response.streamCacheTtlSeconds(), AUTHENTICATED_STREAM_MAX_TTL_SECONDS)
        if (ttlSeconds <= 0L) return
        try {
            cache.set(key, CacheJson.encodeToString(StreamResponse.serializer(), response), ttlSeconds)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.warn("authenticated_sabr_stream_cache event=set_failed", error)
        }
    }

    private companion object {
        const val AUTHENTICATED_STREAM_CACHE_AREA = "stream-youtube-sabr-auth-v1"
        const val AUTHENTICATED_STREAM_MAX_TTL_SECONDS = 300L
        val logger = LoggerFactory.getLogger(YoutubeSessionSabrStreamService::class.java)
    }
}

fun ExtractionResult<StreamResponse>.requiresYoutubeSession(): Boolean =
    when (this) {
        is ExtractionResult.Success -> data.requiresMembership
        is ExtractionResult.BadRequest -> code == "age_restricted" || code == "members_only"
        is ExtractionResult.Failure -> false
    }
