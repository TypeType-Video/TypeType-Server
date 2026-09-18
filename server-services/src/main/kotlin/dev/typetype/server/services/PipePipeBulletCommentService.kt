package dev.typetype.server.services

import dev.typetype.server.models.BulletCommentItem
import dev.typetype.server.models.BulletCommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem

class PipePipeBulletCommentService : BulletCommentService {

    override suspend fun getBulletComments(
        url: String,
        nextpage: String?,
    ): ExtractionResult<BulletCommentsPageResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                val service = NewPipe.getServiceByUrl(url)
                if (!service.serviceInfo.mediaCapabilities.contains(MediaCapability.BULLET_COMMENTS)) {
                    return@runCatching BulletCommentsPageResponse(emptyList(), null)
                }
                if (service.serviceId == BILIBILI_SERVICE_ID && page == null) {
                    withTimeout(30_000L) { service.getStreamExtractor(url).fetchPage() }
                }
                withTimeout(30_000L) {
                    if (page == null) {
                        BulletCommentsInfo.getInfo(url)?.toPageResponse()
                            ?: BulletCommentsPageResponse(emptyList(), null)
                    } else {
                        BulletCommentsInfo.getMoreItems(service, url, page).toPageResponse()
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Bullet comment extraction failed") },
            )
        }

    private fun BulletCommentsInfo.toPageResponse(): BulletCommentsPageResponse =
        BulletCommentsPageResponse(
            comments = relatedItems.map { it.toBulletCommentItem() },
            nextpage = nextPage?.toCursor(),
        )

    private fun org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<BulletCommentsInfoItem>.toPageResponse():
        BulletCommentsPageResponse =
        BulletCommentsPageResponse(
            comments = items.map { it.toBulletCommentItem() },
            nextpage = nextPage?.toCursor(),
        )

    private fun BulletCommentsInfoItem.toBulletCommentItem(): BulletCommentItem = BulletCommentItem(
        text = commentText ?: "",
        argbColor = argbColor,
        position = position?.name ?: BulletCommentsInfoItem.Position.REGULAR.name,
        relativeFontSize = relativeFontSize,
        durationMs = duration?.toMillis() ?: 0L,
        isLive = isLive,
    )
}
