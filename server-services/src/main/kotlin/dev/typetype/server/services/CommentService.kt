package dev.typetype.server.services

import dev.typetype.server.models.CommentsPageResponse
import dev.typetype.server.models.ExtractionResult

interface CommentService {
    suspend fun getComments(url: String, nextpage: String?): ExtractionResult<CommentsPageResponse>
}
