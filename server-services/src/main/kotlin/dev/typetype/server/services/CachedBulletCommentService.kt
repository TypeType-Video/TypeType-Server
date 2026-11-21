package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.BulletCommentsPageResponse
import dev.typetype.server.models.ExtractionResult

class CachedBulletCommentService(
    private val delegate: BulletCommentService,
    private val cache: CacheService,
) : BulletCommentService {

    override suspend fun getBulletComments(
        url: String,
        nextpage: String?,
    ): ExtractionResult<BulletCommentsPageResponse> {
        if (isBilibiliLiveUrl(url)) {
            return delegate.getBulletComments(url, nextpage)
        }
        return PublicExtractionCache.getOrLoad(
            cache = cache,
            area = PublicCacheArea(
                name = "bullet-comments",
                serializer = BulletCommentsPageResponse.serializer(),
                keepLastGood = nextpage == null,
            ) { response ->
                if (response.comments.any { it.isLive }) {
                    CacheAdmission.Valid(0L)
                } else {
                    PublicCacheAdmissions.firstPage(
                        nextpage = nextpage,
                        empty = response.comments.isEmpty(),
                        ttlSeconds = PublicCachePolicy.bulletCommentsTtl(url, nextpage),
                    )
                }
            },
            key = PublicCacheKey.of("bullet-comments", url, nextpage),
        ) { delegate.getBulletComments(url, nextpage) }
    }
}
