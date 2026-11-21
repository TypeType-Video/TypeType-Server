package dev.typetype.server.routes

import dev.typetype.server.models.ErrorResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.FocusBlockedErrorResponse
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.services.AccessControlProfile
import dev.typetype.server.services.BlockedContentProfile
import dev.typetype.server.services.FocusBlockReason
import dev.typetype.server.services.FocusChannelAccess
import dev.typetype.server.services.FocusProfile
import dev.typetype.server.services.FocusService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

internal suspend fun ApplicationCall.respondIfStreamGuardBlocks(
    access: AccessControlProfile?,
    userId: String?,
    focusService: FocusService?,
    url: String,
    resolveStream: suspend () -> ExtractionResult<StreamResponse>,
): Boolean {
    val focus = userId?.let { focusService?.profileFor(it) }?.takeIf { it.strictBlocking }
    if (access?.enabled != true && focus == null) return false
    return when (val result = resolveStream()) {
        is ExtractionResult.Success -> {
            if (access != null && access.enabled && !access.allowsUploader(result.data.uploaderUrl, result.data.uploaderName)) {
                respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
                true
            } else {
                respondIfFocusBlocksStream(focus, url, result.data)
            }
        }
        is ExtractionResult.Failure -> {
            respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(result.message, result.code))
            true
        }
        is ExtractionResult.BadRequest -> {
            respond(HttpStatusCode.BadRequest, ErrorResponse(result.message, result.code))
            true
        }
    }
}

internal suspend fun ApplicationCall.respondIfStreamResponseBlocked(
    access: AccessControlProfile,
    blocked: BlockedContentProfile,
    focus: FocusProfile?,
    url: String,
    stream: StreamResponse,
): Boolean {
    if (!access.allowsUploader(stream.uploaderUrl, stream.uploaderName)) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is not allowed"))
        return true
    }
    if (!blocked.allowsRequestedVideo(url, stream.uploaderUrl, stream.uploaderName)) {
        respond(HttpStatusCode.Forbidden, ErrorResponse("Channel is blocked", "content_blocked"))
        return true
    }
    return respondIfFocusBlocksStream(focus, url, stream)
}

internal suspend fun ApplicationCall.respondIfFocusBlocksStream(
    focus: FocusProfile?,
    url: String,
    stream: StreamResponse,
): Boolean {
    if (focus == null || !focus.strictBlocking) return false
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
    if (focus == null || !focus.strictBlocking) return false
    val reason = when (focus.accessFor(channelUrl)) {
        FocusChannelAccess.ALLOW_STUDY,
        FocusChannelAccess.ENTERTAINMENT,
        -> return false
        FocusChannelAccess.PENDING_STUDY ->
            FocusBlockReason.StudyPending(requireNotNull(focus.pendingUnlockAt(channelUrl)))
        FocusChannelAccess.OUTSIDE_STUDY -> FocusBlockReason.OutsideStudy
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
    FocusBlockReason.OutsideStudy -> FocusBlockedErrorResponse(
        error = "Focus mode restricts videos outside your study group",
        code = "focus_channel_blocked",
        channelUrl = channelUrl,
        channelName = channelName,
        channelAvatarUrl = channelAvatarUrl,
        videoTitle = videoTitle,
    )
}
