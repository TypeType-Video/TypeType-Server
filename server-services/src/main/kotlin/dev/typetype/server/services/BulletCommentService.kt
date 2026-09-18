package dev.typetype.server.services

import dev.typetype.server.models.BulletCommentsPageResponse
import dev.typetype.server.models.ExtractionResult

interface BulletCommentService {
    suspend fun getBulletComments(url: String, nextpage: String?): ExtractionResult<BulletCommentsPageResponse>
}
