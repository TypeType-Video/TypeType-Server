package dev.typetype.server.routes

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.PublicHlsManifestTokenService
import dev.typetype.server.services.ProviderMediaType
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_CODE
import dev.typetype.server.services.YOUTUBE_SESSION_REQUIRED_ERROR
import dev.typetype.server.services.requiresYoutubeSession

private const val STREAMS_CACHE_CONTROL = "public, max-age=21600, stale-while-revalidate=3600"
private const val AUTHENTICATED_STREAMS_CACHE_CONTROL = "no-store"
private const val PROVIDER_STREAMS_CACHE_CONTROL = "no-store"

internal fun streamCacheControl(
    deliveryMode: StreamDeliveryMode,
    isLive: Boolean,
    userId: String?,
): String = when {
    deliveryMode.isSabr() && isLive -> PROVIDER_STREAMS_CACHE_CONTROL
    userId != null -> AUTHENTICATED_STREAMS_CACHE_CONTROL
    deliveryMode == StreamDeliveryMode.NicoNico ||
        deliveryMode == StreamDeliveryMode.BiliBili -> PROVIDER_STREAMS_CACHE_CONTROL
    else -> STREAMS_CACHE_CONTROL
}

internal fun providerMediaType(deliveryMode: StreamDeliveryMode): ProviderMediaType? = when (deliveryMode) {
    StreamDeliveryMode.NicoNico -> ProviderMediaType.NICONICO
    StreamDeliveryMode.BiliBili -> ProviderMediaType.BILIBILI
    StreamDeliveryMode.YoutubeSabr -> null
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
    val authenticatedResult = userId?.let { authenticatedInfo(it, url) }
    if (authenticatedResult != null) {
        val authenticatedLive = (authenticatedResult as? ExtractionResult.Success)?.data?.isLive == true
        val publicLive = (publicResult as? ExtractionResult.Success)?.data?.isLive == true
        if (!authenticatedLive || !publicLive) {
            return StreamResolution(authenticatedResult, authenticated = true)
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
