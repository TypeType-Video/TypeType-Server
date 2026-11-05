package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class CommentItem(
    val id: String,
    val text: String,
    val author: String,
    val authorUrl: String,
    val authorAvatarUrl: String,
    val likeCount: Long,
    val textualLikeCount: String,
    val publishedTime: String,
    val publishedAt: Long? = null,
    val isHeartedByUploader: Boolean,
    val isPinned: Boolean,
    val uploaderVerified: Boolean,
    val replyCount: Int,
    val repliesPage: String?,
    val image: CommentImage? = null,
    val voiceReply: CommentVoiceReply? = null,
)

@Serializable
data class CommentImage(
    val url: String?,
    val animated: Boolean,
    val unavailableText: String? = null,
)

@Serializable
data class CommentVoiceReply(
    val url: String,
    val durationMs: Long,
    val transcript: String,
    val playable: Boolean,
    val unavailableText: String? = null,
)
