package dev.typetype.server.services

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PublicPlaylistResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class PipePipePublicPlaylistService : PublicPlaylistService {
    override suspend fun getPlaylist(url: String, nextpage: String?): ExtractionResult<PublicPlaylistResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null
            val service = runCatching { NewPipe.getServiceByUrl(url) }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported playlist URL") }
            if (service.serviceId != YOUTUBE_SERVICE_ID) {
                return@withContext ExtractionResult.BadRequest("Public playlists are only supported for YouTube")
            }

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) PlaylistInfo.getInfo(service, url).toPublicPlaylistResponse()
                        else PlaylistInfo.getMoreItems(service, url, page).toPublicPlaylistResponse(url)
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Playlist extraction failed") }
            )
        }

    private fun PlaylistInfo.toPublicPlaylistResponse(): PublicPlaylistResponse = PublicPlaylistResponse(
        playlist = toPublicPlaylistItem(),
        videos = relatedItems.map { it.toVideoItem() },
        nextpage = nextPage?.toCursor(),
    )

    private fun InfoItemsPage<StreamInfoItem>.toPublicPlaylistResponse(url: String): PublicPlaylistResponse =
        PublicPlaylistResponse(
            playlist = emptyPublicPlaylistItem(url),
            videos = items.map { it.toVideoItem() },
            nextpage = nextPage?.toCursor(),
        )
}
