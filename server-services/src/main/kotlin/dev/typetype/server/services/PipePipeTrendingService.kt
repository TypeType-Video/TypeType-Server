package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipeTrendingService(
    private val bilibiliTrendingService: BilibiliTrendingService,
    private val nicoNicoTrendingService: NicoNicoTrendingService,
) : TrendingService {

    override suspend fun getTrending(serviceId: Int): ExtractionResult<List<VideoItem>> =
        when (serviceId) {
            BILIBILI_SERVICE_ID -> bilibiliTrendingService.getTrending()
            NICONICO_SERVICE_ID -> nicoNicoTrendingService.getTrending()
            else -> fetchViaPipePipe(serviceId)
        }

    private suspend fun fetchViaPipePipe(serviceId: Int): ExtractionResult<List<VideoItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(30_000L) {
                    val service = NewPipe.getService(serviceId)
                    val extractor = service.kioskList.getDefaultKioskExtractor()
                        ?: return@withTimeout emptyList<VideoItem>()
                    extractor.fetchPage()
                    extractor.initialPage.items
                        .filterIsInstance<StreamInfoItem>()
                        .map { it.toVideoItem() }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Trending failed") }
            )
        }
}
