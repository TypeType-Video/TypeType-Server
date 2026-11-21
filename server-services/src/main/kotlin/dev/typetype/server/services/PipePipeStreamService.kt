package dev.typetype.server.services

import dev.typetype.server.PlaybackTraceLog
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.AudioStreamItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.VideoStreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

class PipePipeStreamService(
    private val cache: CacheService,
    private val subtitleService: YouTubeSubtitleService,
    private val bilibiliRelatedService: BilibiliRelatedService,
    private val sabrInfoSink: ((String, YoutubeSabrInfo) -> Unit)? = null,
    private val fetchSupplementalSubtitles: Boolean = true,
    private val earlyMediaWarmup: ((ProviderMediaType, List<VideoStreamItem>, List<AudioStreamItem>) -> Unit)? = null,
    private val sponsorBlockSegments: SponsorBlockSegmentResolver =
        SponsorBlockSegmentResolver(cache, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
) : StreamService {

    override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> =
        withContext(Dispatchers.IO) {
            runExtractionCatching {
                withExtractionRetry {
                    val (service, linkHandler, extractor) = playbackTracePhase("extractor_setup") {
                        val service = NewPipe.getServiceByUrl(url)
                        val linkHandler = service.streamLHFactory.fromUrl(url)
                        val extractor: StreamExtractor = service.getStreamExtractor(linkHandler)
                        Triple(service, linkHandler, extractor)
                    }
                    playbackTracePhase("extractor_fetch_page") {
                        withTimeout(30_000L) { runPipePipeCall { extractor.fetchPage() } }
                    }
                    startEarlyMediaWarmup(service.serviceId, extractor)
                    coroutineScope {
                        val streamInfoDeferred = async {
                            playbackTracePhase("stream_info") {
                                withTimeout(30_000L) { runPipePipeCall { StreamInfo.getInfo(extractor) } }
                            }
                        }
                        val segmentsDeferred = async {
                            playbackTracePhase("sponsorblock") { resolveSegments(extractor) }
                        }
                        val streamInfo = streamInfoDeferred.await()
                        rememberSabrInfo(streamInfo)
                        val sponsorBlock = segmentsDeferred.await()
                        streamInfo.setSponsorBlockSegments(sponsorBlock.segments)
                        val response = StreamAudioContractResolver.apply(streamInfo.toStreamResponse())
                            .copy(sponsorBlockAvailable = sponsorBlock.available)
                        val withSubtitles = when {
                            !fetchSupplementalSubtitles -> response.also {
                                PlaybackTraceLog.record("supplemental_subtitles", "status=disabled")
                            }
                            response.subtitles.isNotEmpty() -> response.also {
                                PlaybackTraceLog.record("supplemental_subtitles", "status=already_present")
                            }
                            service.serviceId != 0 -> response.also {
                                PlaybackTraceLog.record("supplemental_subtitles", "status=not_applicable")
                            }
                            else -> {
                                var subtitleStatus = "loaded"
                                val subtitles = playbackTracePhase("supplemental_subtitle_fetch") {
                                    try {
                                        subtitleService.fetchSubtitles(streamInfo.id)
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: Exception) {
                                        subtitleStatus = "failed:${error.javaClass.simpleName}"
                                        emptyList()
                                    }
                                }
                                PlaybackTraceLog.record(
                                    "supplemental_subtitles",
                                    "status=$subtitleStatus count=${subtitles.size}",
                                )
                                response.copy(subtitles = subtitles)
                            }
                        }
                        if (service.serviceId == BILIBILI_SERVICE_ID) {
                            playbackTracePhase("bilibili_related_streams") {
                                bilibiliRelatedService.patchRelatedStreams(withSubtitles, linkHandler.url)
                            }
                        } else withSubtitles
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { StreamExtractionErrorMapper.map(it, sourceUrl = url) }
            )
        }

    private fun startEarlyMediaWarmup(serviceId: Int, extractor: StreamExtractor) {
        val warmup = earlyMediaWarmup ?: return
        val provider = when (serviceId) {
            BILIBILI_SERVICE_ID -> ProviderMediaType.BILIBILI
            NICONICO_SERVICE_ID -> ProviderMediaType.NICONICO
            else -> return
        }
        runCatching {
            val id = extractor.id
            warmup(
                provider,
                extractor.videoOnlyStreams.map { it.toVideoStreamItem(id, isVideoOnly = true) },
                extractor.audioStreams.map { it.toAudioStreamItem(id) },
            )
        }
    }

    private fun rememberSabrInfo(streamInfo: StreamInfo): Unit {
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

    /**
     * A signed-in playback (age-restricted or members-only videos) is built without the public extractor, so it
     * carries no SponsorBlock segments: they are looked up here by id, through the same resolver and cache.
     */
    suspend fun withSponsorBlock(url: String, response: StreamResponse): StreamResponse {
        if (response.sponsorBlockSegments.isNotEmpty()) return response
        val extractor = runCatching { NewPipe.getServiceByUrl(url).getStreamExtractor(url) }.getOrNull()
            ?: return response
        val resolution = resolveSegments(extractor)
        return response.copy(
            sponsorBlockSegments = resolution.segments.map { it.toSegmentItem() },
            sponsorBlockAvailable = resolution.available,
        )
    }

    private suspend fun resolveSegments(extractor: StreamExtractor): SponsorBlockResolution {
        val hasSponsorBlock = extractor.getService().serviceInfo.mediaCapabilities
            .contains(MediaCapability.SPONSORBLOCK)
        if (!hasSponsorBlock) return SponsorBlockResolution(emptyArray(), available = true)
        return sponsorBlockSegments.resolve(extractor.id) {
            runPipePipeCall { SponsorBlockExtractorHelper.getSegments(extractor, ALL_SPONSOR_BLOCK_SETTINGS) }
        }
    }
}

private suspend fun <T> playbackTracePhase(phase: String, block: suspend () -> T): T {
    val startedAt = System.nanoTime()
    var outcome = "ok"
    try {
        return block()
    } catch (error: CancellationException) {
        outcome = "cancelled"
        throw error
    } catch (error: Exception) {
        outcome = "error:${error.javaClass.simpleName}"
        throw error
    } finally {
        PlaybackTraceLog.record(
            "pipepipe_phase",
            "phase=$phase outcome=$outcome durationMs=${(System.nanoTime() - startedAt) / 1_000_000}",
        )
    }
}

suspend fun <T> runPipePipeCall(block: () -> T): T =
    runInterruptible(context = Dispatchers.IO, block = block)
