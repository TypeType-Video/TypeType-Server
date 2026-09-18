package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult

class CachedCommentService(
    private val delegate: CommentService,
    private val cache: CacheService,
) : CommentService {

    override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> =
        PublicExtractionCache.getOrLoad(
            cache = cache,
            area = "comments",
            key = PublicCacheKey.of("comments", url, nextpage),
            serializer = CommentsPageResponse.serializer(),
            ttlSeconds = { PublicCachePolicy.commentsTtl(url, nextpage) },
        ) { delegate.getComments(url, nextpage) }
}
