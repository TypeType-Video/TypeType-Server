package dev.typetype.server.services

import dev.typetype.server.models.CommentImage
import dev.typetype.server.models.CommentVoiceReply
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.comments.CommentsInfoItem

fun Collection<Image>.largestUrl(): String? =
    filter { it.url.isNotBlank() }.maxByOrNull { it.width.toLong() * it.height }?.url?.normalizeHttpSchema()

fun CommentsInfoItem.toCommentImage(): CommentImage? {
    val url = pictures.largestUrl()
    val missing = pictureUnsupportedText.ifBlank { pictureUnavailableText }.takeIf { it.isNotBlank() }
    if (url == null && missing == null) return null
    return CommentImage(
        url = url,
        animated = pictureAnimation == CommentsInfoItem.PictureAnimation.ANIMATED,
        unavailableText = if (url == null) missing else null,
    )
}

fun CommentsInfoItem.toVoiceReply(): CommentVoiceReply? {
    val audio = audioAttachment ?: return null
    val url = audio.url.orEmpty()
    val transcript = audio.transcript.orEmpty()
    if (url.isBlank() && transcript.isBlank()) return null
    return CommentVoiceReply(
        url = url,
        durationMs = audio.durationMillis.coerceAtLeast(0),
        transcript = transcript,
        playable = audio.isPlayable && url.isNotBlank(),
        unavailableText = audio.unavailableText?.takeIf { it.isNotBlank() },
    )
}
