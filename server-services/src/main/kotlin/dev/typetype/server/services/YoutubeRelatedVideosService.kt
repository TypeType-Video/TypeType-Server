package dev.typetype.server.services

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.TimeAgoParser
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeLockupStreamInfoItemExtractor
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamInfoItemExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector

fun interface RelatedVideosService {
    suspend fun related(videoUrl: String): ExtractionResult<List<VideoItem>>
}

/**
 * Asks YouTube for the page of a video only ("next"), where the videos shown next to it are:
 * one request. Opening the video the usual way also asks its player, several times over, which
 * is where YouTube answers "sign in to confirm you're not a bot" to an address it distrusts;
 * whoever only wants the neighbours of a video has no use for its player.
 */
class YoutubeRelatedVideosService : RelatedVideosService {
    override suspend fun related(videoUrl: String): ExtractionResult<List<VideoItem>> {
        val videoId = youtubeVideoId(videoUrl)
            ?: return ExtractionResult.BadRequest("Not a YouTube video: $videoUrl")
        return runExtractionCatching {
            runInterruptible(Dispatchers.IO) {
                val youtube = ServiceList.YouTube
                val localization = youtube.localization
                val body = JsonWriter.string(
                    YoutubeParsingHelper.prepareDesktopJsonBuilder(localization, youtube.contentCountry)
                        .value("videoId", videoId)
                        .value("contentCheckOk", true)
                        .value("racyCheckOk", true)
                        .done(),
                ).toByteArray()
                val page = YoutubeParsingHelper.getJsonPostResponse("next", body, localization)
                relatedVideosOf(page, youtube.getTimeAgoParser(localization))
            }
        }.fold(
            onSuccess = { ExtractionResult.Success(it) },
            onFailure = { StreamExtractionErrorMapper.map(it, sourceUrl = videoUrl) },
        )
    }
}

internal fun relatedVideosOf(page: JsonObject, timeAgo: TimeAgoParser?): List<VideoItem> {
    val collector = StreamInfoItemsCollector(ServiceList.YouTube.serviceId)
    page.getObject("contents").getObject("twoColumnWatchNextResults").getObject("secondaryResults")
        .getObject("secondaryResults").getArray("results")
        .filterIsInstance<JsonObject>()
        .forEach { result ->
            val lockup = result.getObject("lockupViewModel")
            when {
                result.has("compactVideoRenderer") ->
                    collector.commit(YoutubeStreamInfoItemExtractor(result.getObject("compactVideoRenderer"), timeAgo))
                result.has("lockupViewModel") && lockup.getString("contentType") == "LOCKUP_CONTENT_TYPE_VIDEO" ->
                    collector.commit(YoutubeLockupStreamInfoItemExtractor(lockup, timeAgo))
            }
        }
    return collector.items.mapNotNull { runCatching { it.toVideoItem() }.getOrNull() }
}
