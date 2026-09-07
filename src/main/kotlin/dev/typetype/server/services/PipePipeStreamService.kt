package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SponsorBlockSegmentItem
import dev.typetype.server.models.StreamResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockApiSettings
import org.schabi.newpipe.extractor.sponsorblock.SponsorBlockExtractorHelper
import dev.typetype.server.sabr.YoutubeSabrInfo
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo

private val ALL_SPONSOR_BLOCK_SETTINGS = SponsorBlockApiSettings().also {
    it.includeSponsorCategory = true
    it.includeIntroCategory = true
    it.includeOutroCategory = true
    it.includeInteractionCategory = true
    it.includeHighlightCategory = true
    it.includeSelfPromoCategory = true
    it.includeMusicCategory = true
    it.includePreviewCategory = true
    it.includeFillerCategory = true
}

internal class PipePipeStreamService(
    private val cache: CacheService,
    private val subtitleService: YouTubeSubtitleService,
    private val bilibiliRelatedService: BilibiliRelatedService,
    private val sabrInfoSink: (suspend (String, YoutubeSabrInfo) -> Unit)? = null,
) : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                withExtractionRetry {
                    val service = NewPipe.getServiceByUrl(url)
                    val linkHandler = service.streamLHFactory.fromUrl(url)
                    val extractor: StreamExtractor = service.getStreamExtractor(linkHandler)
                    withTimeout(30_000L) { runPipePipeCall { extractor.fetchPage() } }
                    coroutineScope {
                        val streamInfoDeferred = async {
                            withTimeout(30_000L) { runPipePipeCall { StreamInfo.getInfo(extractor) } }
                        }
                        val segmentsDeferred = async { resolveSegments(extractor) }
                        val streamInfo = streamInfoDeferred.await()
                        rememberSabrInfo(streamInfo)
                        streamInfo.setSponsorBlockSegments(segmentsDeferred.await())
                        val response = StreamAudioContractResolver.apply(streamInfo.toStreamResponse())
                        val withSubtitles = if (response.subtitles.isEmpty() && service.serviceId == 0) {
                            response.copy(subtitles = subtitleService.fetchSubtitles(streamInfo.id))
                        } else {
                            response
                        }
                        if (service.serviceId == BILIBILI_SERVICE_ID) bilibiliRelatedService.patchRelatedStreams(withSubtitles, linkHandler.url)
                        else withSubtitles
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { StreamExtractionErrorMapper.map(it, sourceUrl = url) }
            )
        }

    private suspend fun rememberSabrInfo(streamInfo: StreamInfo): Unit {
        val sink = sabrInfoSink ?: return
        val info = sequence {
            streamInfo.videoStreams.forEach { yield(it.deliveryMethodInfo) }
            streamInfo.videoOnlyStreams.forEach { yield(it.deliveryMethodInfo) }
            streamInfo.audioStreams.forEach { yield(it.deliveryMethodInfo) }
        }.filterIsInstance<YoutubeSabrInfo>()
            .firstOrNull { it.videoId == streamInfo.id }
            ?: return
        sink(streamInfo.id, info)
    }

    private suspend fun resolveSegments(
        extractor: StreamExtractor,
    ): Array<org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment> {
        val hasSponsorBlock = extractor.getService().serviceInfo.mediaCapabilities
            .contains(MediaCapability.SPONSORBLOCK)
        if (!hasSponsorBlock) return emptyArray()
        val cacheKey = "sponsorblock:${extractor.id}"
        runCatching { cache.get(cacheKey) }.getOrNull()?.let { cached ->
            return runCatching {
                val items = CacheJson.decodeFromString<List<SponsorBlockSegmentItem>>(cached)
                items.toSponsorBlockSegments()
            }.getOrElse { fetchAndCacheSegments(extractor, cacheKey) }
        }
        return fetchAndCacheSegments(extractor, cacheKey)
    }

    private suspend fun fetchAndCacheSegments(
        extractor: StreamExtractor,
        cacheKey: String,
    ): Array<org.schabi.newpipe.extractor.sponsorblock.SponsorBlockSegment> {
        val segments = runCatching {
            withTimeout(15_000L) {
                runPipePipeCall { SponsorBlockExtractorHelper.getSegments(extractor, ALL_SPONSOR_BLOCK_SETTINGS) }
            }
        }.getOrElse { emptyArray() }
        runCatching {
            val items = segments.map { it.toSegmentItem() }
            cache.set(cacheKey, CacheJson.encodeToString(items), SPONSORBLOCK_TTL_SECONDS)
        }
        return segments
    }

    private companion object {
        const val SPONSORBLOCK_TTL_SECONDS = 21600L
    }
}

internal suspend fun <T> runPipePipeCall(block: () -> T): T =
    runInterruptible(context = Dispatchers.IO, block = block)
