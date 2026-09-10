package dev.typetype.server.services

import dev.typetype.server.models.CommentItem
import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

internal fun String.normalizeHttpSchema(): String = replace("httpss://", "https://")

class PipePipeCommentService : CommentService {

    override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> =
        withContext(Dispatchers.IO) {
            val page = if (nextpage != null) {
                runCatching { nextpage.toPage() }
                    .getOrElse { return@withContext ExtractionResult.BadRequest("Invalid nextpage cursor") }
            } else null

            runCatching {
                withExtractionRetry {
                    withTimeout(30_000L) {
                        if (page == null) {
                            val info = CommentsInfo.getInfo(url)
                            info?.toPageResponse() ?: CommentsPageResponse(emptyList(), null, commentsDisabled = false)
                        } else {
                            val service = NewPipe.getServiceByUrl(url)
                            CommentsInfo.getMoreItems(service, url, page).toPageResponse()
                        }
                    }
                }
            }.fold(
                onSuccess = { ExtractionResult.Success(it) },
                onFailure = { ExtractionResult.Failure(it.message ?: "Comment extraction failed") }
            )
        }

    private fun CommentsInfo.toPageResponse(): CommentsPageResponse = CommentsPageResponse(
        comments = relatedItems.map { it.toCommentItem() },
        nextpage = nextPage?.toCursor(),
        commentsDisabled = isCommentsDisabled,
    )

    private fun org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage<CommentsInfoItem>.toPageResponse(): CommentsPageResponse =
        CommentsPageResponse(
            comments = items.map { it.toCommentItem() },
            nextpage = nextPage?.toCursor(),
            commentsDisabled = false,
        )

    private fun CommentsInfoItem.toCommentItem(): CommentItem = CommentItem(
        id = commentId ?: "",
        text = commentText.content,
        author = uploaderName ?: "",
        authorUrl = uploaderUrl ?: "",
        authorAvatarUrl = (uploaderAvatarUrl ?: "").normalizeHttpSchema(),
        likeCount = likeCount.toLong(),
        textualLikeCount = textualLikeCount ?: "",
        publishedAt = PublishedAtMapper.fromUploaded(uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: -1L),
        publishedTime = textualUploadDate ?: "",
        isHeartedByUploader = isHeartedByUploader,
        isPinned = isPinned,
        uploaderVerified = isUploaderVerified,
        replyCount = replyCount,
        repliesPage = replies?.toCursor(),
    )
}
