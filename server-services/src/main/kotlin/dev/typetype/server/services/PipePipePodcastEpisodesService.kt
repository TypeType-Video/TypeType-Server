package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PodcastEpisodesResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipePodcastEpisodesService {

    suspend fun getPodcastEpisodes(url: String, nextpage: String?): ExtractionResult<PodcastEpisodesResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null
            val service = runCatching { NewPipe.getServiceByUrl(url) }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported podcast URL") }
            if (service.serviceId != YOUTUBE_SERVICE_ID) {
                return@withContext ExtractionResult.BadRequest("Podcasts are only supported for YouTube playlists")
            }

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) PlaylistInfo.getInfo(service, url).toPodcastEpisodesResponse()
                        else PlaylistInfo.getMoreItems(service, url, page).toPodcastEpisodesResponse(url)
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Podcast episodes extraction failed") }
            )
        }

    private fun PlaylistInfo.toPodcastEpisodesResponse(): PodcastEpisodesResponse = PodcastEpisodesResponse(
        podcast = toPodcastItem(),
        episodes = relatedItems.map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun InfoItemsPage<StreamInfoItem>.toPodcastEpisodesResponse(url: String): PodcastEpisodesResponse =
        PodcastEpisodesResponse(
            podcast = emptyPodcastItem(url),
            episodes = items.map { it.toVideoItem() },
            nextpage = nextPage?.toCursor(),
        )
}
