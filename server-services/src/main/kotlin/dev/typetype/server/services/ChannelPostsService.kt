package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelPostsResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelTabInfo
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem
import org.schabi.newpipe.extractor.linkhandler.ChannelTabs

interface ChannelPostsService {
    suspend fun getPosts(url: String, nextpage: String?): ExtractionResult<ChannelPostsResponse>
}

class PipePipeChannelPostsService : ChannelPostsService {

    override suspend fun getPosts(url: String, nextpage: String?): ExtractionResult<ChannelPostsResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null
            val channelUrl = url.toPostsChannelUrl()
            val service = runCatching { NewPipe.getServiceByUrl(channelUrl) }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported channel URL") }
            if (service.serviceId != YOUTUBE_SERVICE_ID) {
                return@withContext ExtractionResult.BadRequest("Posts are only supported for YouTube channels")
            }
            val channelId = runCatching { service.channelLHFactory.fromUrl(channelUrl).id }
                .getOrElse { return@withContext ExtractionResult.BadRequest("Unsupported channel URL") }

            runExtractionCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        YoutubeSessionTokenScope.withoutCredentials { extractPage(service, channelId, channelUrl, page) }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { transientAwareFailure(it, "Channel posts extraction failed") },
            )
        }

    private fun extractPage(service: StreamingService, channelId: String, channelUrl: String, page: Page?): ChannelPostsResponse {
        val extractor = service.channelTabExtractor(channelUrl, channelId, ChannelTabs.POSTS, null)
        if (page == null) {
            extractor.fetchPage()
            val info = ChannelTabInfo.getInfo(extractor)
            return info.relatedItems.toResponse(channelUrl, info.nextPage?.toCursor())
        }
        val more = extractor.getPage(page)
        return more.items.toResponse(channelUrl, more.nextPage?.toCursor())
    }

    private fun List<InfoItem>.toResponse(channelUrl: String, nextpage: String?): ChannelPostsResponse {
        val posts = filterIsInstance<CommunityPostInfoItem>().map { it.toCommunityPostItem() }
        return ChannelPostsResponse(
            channelName = posts.firstOrNull { it.authorName.isNotBlank() }?.authorName.orEmpty(),
            channelUrl = channelUrl,
            posts = posts,
            nextpage = nextpage,
        )
    }
}

class CachedChannelPostsService(
    private val delegate: ChannelPostsService,
    private val cache: CacheService,
) : ChannelPostsService {

    override suspend fun getPosts(url: String, nextpage: String?): ExtractionResult<ChannelPostsResponse> {
        val key = PublicCacheKey.of("channel-posts", url, nextpage)
        runCatching { cache.get(key) }.getOrNull()?.let { cached ->
            runCatching { CacheJson.decodeFromString<ChannelPostsResponse>(cached) }.getOrNull()
                ?.let { return ExtractionResult.Success(it) }
        }
        val result = delegate.getPosts(url, nextpage)
        if (result is ExtractionResult.Success && result.data.posts.isNotEmpty()) {
            runCatching { cache.set(key, CacheJson.encodeToString(ChannelPostsResponse.serializer(), result.data), TTL_SECONDS) }
        }
        return result
    }

    private companion object {
        const val TTL_SECONDS = 900L
    }
}

private fun String.toPostsChannelUrl(): String {
    val normalized = trim().substringBefore('?').substringBefore('#').trimEnd('/')
    val index = Regex("/(posts|community)$", RegexOption.IGNORE_CASE).find(normalized)?.range?.first ?: return normalized
    return normalized.substring(0, index)
}
