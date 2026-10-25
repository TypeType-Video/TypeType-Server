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
            area = PublicCacheArea("comments", CommentsPageResponse.serializer(), keepLastGood = nextpage == null) {
                val empty = it.comments.isEmpty() && !it.commentsDisabled
                PublicCacheAdmissions.firstPage(nextpage, empty, PublicCachePolicy.commentsTtl(url, nextpage))
            },
            key = PublicCacheKey.of("comments", url, nextpage),
        ) { delegate.getComments(url, nextpage) }
}
