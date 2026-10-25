package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_CODE
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_ERROR
import dev.typetype.server.services.requiresYoutubeSession
import dev.typetype.server.services.YoutubeTypeTypeMapper

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val AUTHENTICATED_STREAMS_CACHE_CONTROL = "no-store"
private const val PROVIDER_STREAMS_CACHE_CONTROL = "no-store"

internal fun streamCacheControl(
    deliveryMode: StreamDeliveryMode,
    isLive: Boolean,
    userId: String?,
): String = when {
    deliveryMode.isSabr() && isLive -> PROVIDER_STREAMS_CACHE_CONTROL
    deliveryMode == StreamDeliveryMode.YoutubeLiveHls -> PROVIDER_STREAMS_CACHE_CONTROL
    userId != null -> AUTHENTICATED_STREAMS_CACHE_CONTROL
    deliveryMode == StreamDeliveryMode.NicoNico ||
        deliveryMode == StreamDeliveryMode.BiliBili -> PROVIDER_STREAMS_CACHE_CONTROL
    else -> STREAMS_CACHE_CONTROL
}

internal fun providerMediaType(deliveryMode: StreamDeliveryMode): ProviderMediaType? = when (deliveryMode) {
    StreamDeliveryMode.NicoNico -> ProviderMediaType.NICONICO
    StreamDeliveryMode.BiliBili -> ProviderMediaType.BILIBILI
    StreamDeliveryMode.YoutubeSabr -> null
    StreamDeliveryMode.YoutubeLiveHls -> null
}

internal data class StreamResolution(
    val result: ExtractionResult<StreamResponse>,
    val authenticated: Boolean = false,
)

internal suspend fun resolveStreamInfo(
    url: String,
    deliveryMode: StreamDeliveryMode,
    userId: String?,
    publicResult: ExtractionResult<StreamResponse>,
    dependencies: StreamRouteDependencies,
): StreamResolution = resolvePublicOrAuthenticatedStreamInfo(
    url = url,
    deliveryMode = deliveryMode,
    userId = userId,
    publicResult = publicResult,
    dependencies = dependencies,
).withRelatedVideos(url, dependencies.youtubeRelatedVideos)

private suspend fun resolvePublicOrAuthenticatedStreamInfo(
    url: String,
    deliveryMode: StreamDeliveryMode,
    userId: String?,
    publicResult: ExtractionResult<StreamResponse>,
    dependencies: StreamRouteDependencies,
): StreamResolution {
    if (deliveryMode == StreamDeliveryMode.BiliBili) {
        val bilibiliInfo = dependencies.bilibiliSessionStreamInfo
        if (bilibiliInfo != null && userId != null) {
            val bilibiliResult = bilibiliInfo(userId, url)
            if (bilibiliResult != null) return StreamResolution(bilibiliResult, authenticated = true)
        }
        return StreamResolution(publicResult)
    }
    val authenticatedInfo = dependencies.youtubeSessionSabrStreamInfo
    if (!deliveryMode.isSabr() || authenticatedInfo == null) {
        return StreamResolution(publicResult)
    }
    if (!publicResult.requiresYoutubeSession()) {
        return StreamResolution(publicResult)
    }
    val authenticatedResult = userId?.let { authenticatedInfo(it, url) }
    if (authenticatedResult != null) {
        val authenticatedLive = (authenticatedResult as? ExtractionResult.Success)?.data?.isLive == true
        val publicLive = (publicResult as? ExtractionResult.Success)?.data?.isLive == true
        if (!authenticatedLive || !publicLive) {
            return StreamResolution(
                authenticatedResult.withPublicStreamMetadata(publicResult),
                authenticated = true,
            )
        }
    }
    return if (publicResult.requiresYoutubeSession()) {
        StreamResolution(
            ExtractionResult.BadRequest(YOUTUBE_SESSION_REQUIRED_ERROR, YOUTUBE_SESSION_REQUIRED_CODE),
        )
    } else {
        StreamResolution(publicResult)
    }
}

internal suspend fun StreamResolution.withRelatedVideos(
    url: String,
    loader: (suspend (String) -> ExtractionResult<List<VideoItem>>)?,
): StreamResolution {
    val success = result as? ExtractionResult.Success<StreamResponse> ?: return this
    if (success.data.relatedStreams.isNotEmpty() || loader == null) return this
    val related = loader(url) as? ExtractionResult.Success<List<VideoItem>> ?: return this
    return copy(result = ExtractionResult.Success(success.data.copy(relatedStreams = related.data)))
}

internal fun ExtractionResult<StreamResponse>.withPublicStreamMetadata(
    fallback: ExtractionResult<StreamResponse>,
): ExtractionResult<StreamResponse> {
    val primary = this as? ExtractionResult.Success<StreamResponse> ?: return this
    val metadata = (fallback as? ExtractionResult.Success<StreamResponse>)?.data ?: return this
    val title = if (primary.data.hasFallbackYoutubeTitle() && !metadata.hasFallbackYoutubeTitle()) {
        metadata.title
    } else {
        primary.data.title
    }
    return primary.copy(
        data = primary.data.copy(
            title = title,
            thumbnailUrl = primary.data.thumbnailUrl.ifBlank { metadata.thumbnailUrl },
            description = primary.data.description.ifBlank { metadata.description },
            duration = primary.data.duration.takeIf { it > 0L } ?: metadata.duration,
            viewCount = primary.data.viewCount.takeIf { it >= 0L } ?: metadata.viewCount,
            likeCount = primary.data.likeCount.takeIf { it >= 0L } ?: metadata.likeCount,
            dislikeCount = primary.data.dislikeCount.takeIf { it >= 0L } ?: metadata.dislikeCount,
            uploadDate = primary.data.uploadDate.ifBlank { metadata.uploadDate },
            uploaded = primary.data.uploaded.takeIf { it >= 0L } ?: metadata.uploaded,
            uploaderName = primary.data.uploaderName.ifBlank { metadata.uploaderName },
            uploaderUrl = primary.data.uploaderUrl.ifBlank { metadata.uploaderUrl },
            uploaderAvatarUrl = primary.data.uploaderAvatarUrl.ifBlank { metadata.uploaderAvatarUrl },
            uploaderSubscriberCount = primary.data.uploaderSubscriberCount
                .takeIf { it >= 0 }
                ?: metadata.uploaderSubscriberCount,
            uploaderVerified = primary.data.uploaderVerified || metadata.uploaderVerified,
            category = primary.data.category.ifBlank { metadata.category },
            license = primary.data.license.ifBlank { metadata.license },
            visibility = primary.data.visibility.ifBlank { metadata.visibility },
            tags = primary.data.tags.ifEmpty { metadata.tags },
            streamType = primary.data.streamType.ifBlank { metadata.streamType },
            isPostLive = primary.data.isPostLive || metadata.isPostLive,
            isLiveContent = primary.data.isLiveContent || metadata.isLiveContent,
            isShortFormContent = primary.data.isShortFormContent || metadata.isShortFormContent,
            requiresMembership = primary.data.requiresMembership || metadata.requiresMembership,
            streamSegments = primary.data.streamSegments.ifEmpty { metadata.streamSegments },
            originalAudioTrackId = primary.data.originalAudioTrackId ?: metadata.originalAudioTrackId,
            preferredDefaultAudioTrackId = primary.data.preferredDefaultAudioTrackId
                ?: metadata.preferredDefaultAudioTrackId,
            subtitles = primary.data.subtitles.ifEmpty { metadata.subtitles },
            previewFrames = primary.data.previewFrames.ifEmpty { metadata.previewFrames },
            sponsorBlockSegments = primary.data.sponsorBlockSegments
                .ifEmpty { metadata.sponsorBlockSegments },
            relatedStreams = primary.data.relatedStreams.ifEmpty { metadata.relatedStreams },
            publishedAt = primary.data.publishedAt ?: metadata.publishedAt,
            collections = primary.data.collections.ifEmpty { metadata.collections },
            parts = primary.data.parts.ifEmpty { metadata.parts },
        ),
    )
}

private fun StreamResponse.hasFallbackYoutubeTitle(): Boolean =
    title.isBlank() || title == YoutubeTypeTypeMapper.titleForUrl(id)

internal fun StreamResponse.hasPlayableSource(): Boolean =
    videoStreams.isNotEmpty() ||
        videoOnlyStreams.isNotEmpty() ||
        audioStreams.isNotEmpty() ||
        hlsUrl.isNotBlank() ||
        dashMpdUrl.isNotBlank()

internal fun StreamResponse.withSignedPublicHlsUrl(
    shouldSign: Boolean,
    tokenService: PublicHlsManifestTokenService?,
): StreamResponse {
    if (!shouldSign || tokenService == null || hlsUrl.isBlank()) return this
    if (hlsUrl.startsWith("/streams/hls-manifest?token=")) return this
    return copy(hlsUrl = tokenService.createPath(hlsUrl))
}
