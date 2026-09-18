package dev.typetype.server.services

import dev.typetype.server.models.ActiveSessionNowPlayingItem
import dev.typetype.server.models.SessionPlaybackProgressRequest
import dev.typetype.server.models.SessionPlaybackStartRequest

object ActiveSessionNowPlayingMapper {
    fun fromStart(request: SessionPlaybackStartRequest, now: Long): ActiveSessionNowPlayingItem = ActiveSessionNowPlayingItem(
        videoUrl = request.videoUrl.trim(),
        title = request.title.trim(),
        thumbnail = ActiveSessionStrings.text(request.thumbnail),
        channelName = ActiveSessionStrings.text(request.channelName),
        positionMs = request.positionMs.coerceAtLeast(0),
        durationMs = request.durationMs?.coerceAtLeast(0),
        paused = request.paused,
        updatedAt = now,
    )

    fun fromProgress(current: ActiveSessionNowPlayingItem?, request: SessionPlaybackProgressRequest, now: Long): ActiveSessionNowPlayingItem? =
        current?.copy(
            videoUrl = ActiveSessionStrings.text(request.videoUrl) ?: current.videoUrl,
            title = ActiveSessionStrings.text(request.title) ?: current.title,
            thumbnail = ActiveSessionStrings.text(request.thumbnail) ?: current.thumbnail,
            channelName = ActiveSessionStrings.text(request.channelName) ?: current.channelName,
            positionMs = request.positionMs.coerceAtLeast(0),
            durationMs = request.durationMs?.coerceAtLeast(0) ?: current.durationMs,
            paused = request.paused,
            updatedAt = now,
        ) ?: request.toNowPlaying(now)

    private fun SessionPlaybackProgressRequest.toNowPlaying(now: Long): ActiveSessionNowPlayingItem? {
        val normalizedUrl = ActiveSessionStrings.text(videoUrl) ?: return null
        val normalizedTitle = ActiveSessionStrings.text(title) ?: return null
        return ActiveSessionNowPlayingItem(
            videoUrl = normalizedUrl,
            title = normalizedTitle,
            thumbnail = ActiveSessionStrings.text(thumbnail),
            channelName = ActiveSessionStrings.text(channelName),
            positionMs = positionMs.coerceAtLeast(0),
            durationMs = durationMs?.coerceAtLeast(0),
            paused = paused,
            updatedAt = now,
        )
    }
}
