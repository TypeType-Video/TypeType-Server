package dev.typetype.server.routes

import dev.typetype.server.models.FocusBlockedErrorResponse
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.FocusBlockReason
import dev.typetype.server.services.FocusChannelAccess
import dev.typetype.server.services.FocusProfile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.respondIfFocusBlocksStream(
    focus: FocusProfile?,
    url: String,
    stream: StreamResponse,
): Boolean {
    if (focus == null) return false
    val publishedAt = stream.publishedAt ?: stream.uploaded.takeIf { it > 0 }
    val reason = focus.blockReasonForPlayback(url, stream.uploaderUrl, publishedAt) ?: return false
    respond(
        HttpStatusCode.Forbidden,
        focusBlockedBody(
            reason = reason,
            channelUrl = stream.uploaderUrl,
            channelName = stream.uploaderName,
            channelAvatarUrl = stream.uploaderAvatarUrl,
            videoTitle = stream.title,
        ),
    )
    return true
}

internal suspend fun ApplicationCall.respondIfFocusBlocksChannel(
    focus: FocusProfile?,
    channelUrl: String,
    channelName: String = "",
): Boolean {
    if (focus == null) return false
    val reason = when (focus.accessFor(channelUrl)) {
        FocusChannelAccess.ALLOW_STUDY,
        FocusChannelAccess.ENTERTAINMENT,
        -> return false
        FocusChannelAccess.PENDING_STUDY ->
            FocusBlockReason.StudyPending(requireNotNull(focus.pendingUnlockAt(channelUrl)))
        FocusChannelAccess.CHALLENGE_REQUIRED -> FocusBlockReason.ChallengeRequired
    }
    respond(
        HttpStatusCode.Forbidden,
        focusBlockedBody(
            reason = reason,
            channelUrl = channelUrl,
            channelName = channelName,
        ),
    )
    return true
}

internal fun focusBlockedBody(
    reason: FocusBlockReason,
    channelUrl: String = "",
    channelName: String = "",
    channelAvatarUrl: String = "",
    videoTitle: String = "",
): FocusBlockedErrorResponse = when (reason) {
    FocusBlockReason.Entertainment -> FocusBlockedErrorResponse(
        error = "Hidden by Focus mode",
        code = "focus_entertainment_blocked",
        channelUrl = channelUrl,
        channelName = channelName,
        channelAvatarUrl = channelAvatarUrl,
        videoTitle = videoTitle,
    )
    is FocusBlockReason.StudyPending -> FocusBlockedErrorResponse(
        error = "This channel is cooling off before joining your study group",
        code = "focus_study_pending",
        channelUrl = channelUrl,
        channelName = channelName,
        channelAvatarUrl = channelAvatarUrl,
        videoTitle = videoTitle,
        unlockAt = reason.unlockAt,
    )
    FocusBlockReason.ChallengeRequired -> FocusBlockedErrorResponse(
        error = "Focus mode blocks videos outside your study group",
        code = "focus_challenge_required",
        channelUrl = channelUrl,
        channelName = channelName,
        channelAvatarUrl = channelAvatarUrl,
        videoTitle = videoTitle,
    )
}
