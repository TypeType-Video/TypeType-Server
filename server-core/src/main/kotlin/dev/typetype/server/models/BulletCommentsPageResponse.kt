package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class BulletCommentsPageResponse(
    val comments: List<BulletCommentItem>,
    val nextpage: String?,
)
