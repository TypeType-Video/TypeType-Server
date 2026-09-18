package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentsPageResponse(
    val comments: List<CommentItem>,
    val nextpage: String?,
    val commentsDisabled: Boolean,
)
