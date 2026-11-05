package dev.typetype.server.models

import kotlinx.serialization.Serializable

@Serializable
data class ChannelPostsResponse(
    val channelName: String,
    val channelUrl: String,
    val posts: List<CommunityPostItem>,
    val nextpage: String?,
)

@Serializable
data class CommunityPostItem(
    val id: String,
    val url: String,
    val type: String,
    val text: String,
    val authorName: String,
    val authorUrl: String,
    val authorAvatarUrl: String,
    val publishedTime: String,
    val publishedAt: Long? = null,
    val likeCount: Long? = null,
    val textualLikeCount: String,
    val commentCount: Long? = null,
    val textualCommentCount: String,
    val images: List<String> = emptyList(),
    val poll: CommunityPostPoll? = null,
    val video: CommunityPostVideo? = null,
    val sharedPost: CommunityPostItem? = null,
)

@Serializable
data class CommunityPostPoll(
    val type: String,
    val choices: List<CommunityPostPollChoice>,
    val voteCount: Long? = null,
    val textualVoteCount: String,
)

@Serializable
data class CommunityPostPollChoice(
    val text: String,
    val imageUrl: String? = null,
)

@Serializable
data class CommunityPostVideo(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
)
