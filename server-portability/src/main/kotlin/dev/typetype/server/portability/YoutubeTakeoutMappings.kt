package dev.typetype.server.portability

import dev.typetype.server.models.FavoriteItem
import dev.typetype.server.models.HistoryItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.services.YoutubeTypeTypeMapper

fun SubscriptionItem.toPortability() = PortabilitySubscription(
    channelUrl = channelUrl,
    name = name,
    avatarUrl = avatarUrl,
    subscribedAt = subscribedAt,
)

fun HistoryItem.toPortability() = PortabilityHistory(
    video = PortabilityVideo(
        url = url,
        title = title,
        thumbnailUrl = thumbnail.ifBlank { YoutubeTypeTypeMapper.thumbnailForUrl(url) },
        durationSeconds = duration,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatar,
    ),
    watchedAt = watchedAt,
    positionSeconds = progress,
)

fun FavoriteItem.toPortability() = PortabilityFavorite(
    video = PortabilityVideo(
        url = videoUrl,
        title = title.ifBlank { YoutubeTypeTypeMapper.titleForUrl(videoUrl) },
        thumbnailUrl = thumbnail.ifBlank { YoutubeTypeTypeMapper.thumbnailForUrl(videoUrl) },
        durationSeconds = duration,
        channelName = channelName,
        channelUrl = channelUrl,
        channelAvatarUrl = channelAvatar,
        viewCount = viewCount,
        publishedAt = publishedAt,
    ),
    favoritedAt = favoritedAt,
)

fun PlaylistVideoItem.toPortabilityVideo() = PortabilityVideo(
    url = url,
    title = title.ifBlank { YoutubeTypeTypeMapper.titleForUrl(url) },
    thumbnailUrl = thumbnail.ifBlank { YoutubeTypeTypeMapper.thumbnailForUrl(url) },
    durationSeconds = duration,
    channelName = channelName,
    channelUrl = channelUrl,
    channelAvatarUrl = channelAvatar,
    viewCount = viewCount,
    publishedAt = publishedAt,
)
