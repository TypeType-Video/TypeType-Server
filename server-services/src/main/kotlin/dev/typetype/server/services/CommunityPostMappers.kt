package dev.typetype.server.services

import dev.typetype.server.models.CommunityPostItem
import dev.typetype.server.models.CommunityPostPoll
import dev.typetype.server.models.CommunityPostPollChoice
import dev.typetype.server.models.CommunityPostVideo
import org.schabi.newpipe.extractor.community.CommunityPostInfoItem

fun CommunityPostInfoItem.toCommunityPostItem(): CommunityPostItem = CommunityPostItem(
    id = postId.orEmpty(),
    url = url.orEmpty(),
    type = postType.name.lowercase(),
    text = content?.content.orEmpty(),
    authorName = authorName.orEmpty(),
    authorUrl = authorUrl.orEmpty(),
    authorAvatarUrl = authorAvatars.largestUrl().orEmpty(),
    publishedTime = textualUploadDate.orEmpty(),
    publishedAt = uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()?.let(PublishedAtMapper::fromUploaded),
    likeCount = likeCount.knownCount(),
    textualLikeCount = textualLikeCount.orEmpty(),
    commentCount = commentCount.knownCount(),
    textualCommentCount = textualCommentCount.orEmpty(),
    images = images.mapNotNull { it.largestUrl() },
    poll = toPoll(),
    video = toVideo(),
    sharedPost = sharedPost?.toCommunityPostItem(),
)

private fun CommunityPostInfoItem.toPoll(): CommunityPostPoll? {
    if (pollChoices.isEmpty()) return null
    return CommunityPostPoll(
        type = pollType.name.lowercase(),
        choices = pollChoices.map { CommunityPostPollChoice(it.text.orEmpty(), it.images.largestUrl()) },
        voteCount = pollVoteCount.knownCount(),
        textualVoteCount = textualPollVotes.orEmpty(),
    )
}

private fun CommunityPostInfoItem.toVideo(): CommunityPostVideo? {
    val url = sharedUrl?.takeIf { it.isNotBlank() } ?: return null
    return CommunityPostVideo(
        url = url,
        title = sharedTitle.orEmpty(),
        thumbnailUrl = sharedPreviewImages.largestUrl().orEmpty(),
    )
}

private fun Long.knownCount(): Long? = takeIf { it != CommunityPostInfoItem.NO_COUNT && it >= 0 }
