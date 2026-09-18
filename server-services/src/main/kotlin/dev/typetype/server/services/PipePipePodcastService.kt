package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PodcastEpisodesResponse
import dev.typetype.server.models.PodcastPageResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipePodcastService : PodcastService {

    private val episodesService = PipePipePodcastEpisodesService()

    override suspend fun getPodcasts(url: String, nextpage: String?): ExtractionResult<PodcastPageResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null
            val channelUrl = url.toPodcastChannelUrl()
            val service = runCatching { NewPipe.getServiceByUrl(channelUrl) }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported podcast URL") }
            if (service.serviceId != YOUTUBE_SERVICE_ID) {
                return@withContext ExtractionResult.BadRequest("Podcasts are only supported for YouTube channels")
            }
            val channelId = runCatching { service.channelLHFactory.fromUrl(channelUrl).id }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported podcast URL") }

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) { extractPodcastPage(service, channelId, channelUrl, page) }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Podcast extraction failed") }
            )
        }

    override suspend fun getPodcastEpisodes(url: String, nextpage: String?): ExtractionResult<PodcastEpisodesResponse> =
        episodesService.getPodcastEpisodes(url, nextpage)

    private fun extractPodcastPage(
        service: StreamingService,
        channelId: String,
        channelUrl: String,
        page: Page?,
    ): PodcastPageResponse {
        val extractor = service.channelTabExtractor(channelUrl, channelId, ChannelTabs.PODCASTS, null)
        if (page == null) {
            extractor.fetchPage()
            return ChannelTabInfo.getInfo(extractor).toPodcastPageResponse(channelUrl)
        }
        return extractor.getPage(page).toPodcastPageResponse(channelUrl)
    }

    private fun ChannelTabInfo.toPodcastPageResponse(channelUrl: String): PodcastPageResponse =
        relatedItems.toPodcastPageResponse(channelUrl = channelUrl, channelName = name ?: "", nextpage = nextPage?.toCursor())

    private fun InfoItemsPage<InfoItem>.toPodcastPageResponse(channelUrl: String): PodcastPageResponse =
        items.toPodcastPageResponse(channelUrl = channelUrl, channelName = "", nextpage = nextPage?.toCursor())

    private fun List<InfoItem>.toPodcastPageResponse(
        channelUrl: String,
        channelName: String,
        nextpage: String?,
    ): PodcastPageResponse {
        val podcasts = filterIsInstance<PlaylistInfoItem>().map { it.toPodcastItem() }
        val episodes = filterIsInstance<StreamInfoItem>().map { it.toVideoItem() }
        val resolvedChannelName = podcasts.firstOrNull { it.uploaderName.isNotBlank() }?.uploaderName
            ?: episodes.firstOrNull { it.uploaderName.isNotBlank() }?.uploaderName
            ?: channelName
        return PodcastPageResponse(
            channelName = resolvedChannelName,
            channelUrl = channelUrl,
            podcasts = podcasts,
            episodes = episodes,
            nextpage = nextpage,
        )
    }
}

private fun String.toPodcastChannelUrl(): String {
    val normalized = trim().substringBefore('?').substringBefore('#').trimEnd('/')
    val marker = "/podcasts"
    val index = normalized.lowercase().indexOf(marker)
    return if (index == -1) normalized else normalized.substring(0, index).trimEnd('/')
}
