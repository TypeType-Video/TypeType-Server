package dev.typetype.server.services

import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult

class YoutubeScopedCommentService(private val delegate: CommentService) : CommentService {
    override suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse> =
        if (isYoutubeUrl(url)) {
            YoutubeSessionTokenScope.withoutCredentials { delegate.getComments(url, nextpage) }
        } else {
            delegate.getComments(url, nextpage)
        }
}
