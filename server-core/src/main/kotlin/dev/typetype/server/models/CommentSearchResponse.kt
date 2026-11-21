package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentSearchResponse(
    val comments: List<CommentItem>,
    val nextpage: String?,
    val scannedPages: Int,
    val commentsDisabled: Boolean,
)
