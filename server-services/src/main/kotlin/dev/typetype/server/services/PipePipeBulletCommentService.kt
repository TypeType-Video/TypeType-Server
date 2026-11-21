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

class PipePipeBulletCommentService(
    private val bilibiliGuard: ProviderExtractionGuard? = null,
) : BulletCommentService {

    override suspend fun getBulletComments(
        url: String,
        nextpage: String?,
    ): ExtractionResult<BulletCommentsPageResponse> =
        withContext(Dispatchers.IO) {
            if (isBilibiliLiveUrl(url)) {
                return@withContext ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), null))
            }
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            val service = runExtractionCatching {
                NewPipe.getServiceByUrl(url)
            }.getOrElse { error ->
                return@withContext transientAwareFailure(error, "Bullet comment extraction failed")
            }

            if (!service.serviceInfo.mediaCapabilities.contains(MediaCapability.BULLET_COMMENTS)) {
                return@withContext ExtractionResult.Success(BulletCommentsPageResponse(emptyList(), null))
            }

            val load: suspend () -> ExtractionResult<BulletCommentsPageResponse> = {
                runExtractionCatching {
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
                    onFailure = { transientAwareFailure(it, "Bullet comment extraction failed") },
                )
            }

            if (service.serviceId == BILIBILI_SERVICE_ID && bilibiliGuard != null) {
                bilibiliGuard.run(nobodyWaits = false, load)
            } else {
                load()
            }
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

internal fun isBilibiliLiveUrl(url: String): Boolean =
    url.contains("live.bilibili.com", ignoreCase = true)
