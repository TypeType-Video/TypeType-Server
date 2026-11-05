package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.withContext

class BilibiliMirrorSampleSource(
    private val trending: BilibiliTrendingService,
    private val streams: StreamService,
) {
    suspend fun fetch(): BilibiliMirrorWatch.Sample? {
        val videos = (trending.getTrending() as? ExtractionResult.Success)?.data ?: return null
        val video = videos.firstOrNull { it.url.contains("bilibili.com/video/") } ?: return null
        val stream = withContext(StreamRequestWatch.NobodyWaiting.mark) { streams.getStreamInfo(video.url) }
        val media = (stream as? ExtractionResult.Success)?.data?.videoOnlyStreams
            ?.minByOrNull { it.bitrate ?: Int.MAX_VALUE }?.url ?: return null
        return BilibiliMirrorWatch.Sample(listOf(media) + bilibiliUposMirrorAlternates(media), null)
    }
}
