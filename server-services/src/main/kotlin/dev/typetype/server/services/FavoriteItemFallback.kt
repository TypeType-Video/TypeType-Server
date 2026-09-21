package dev.typetype.server.services

import dev.typetype.server.models.FavoriteItem

fun FavoriteItem.withYoutubeFallbackTitle(): FavoriteItem =
    if (title.isNotBlank()) this else copy(title = YoutubeTypeTypeMapper.titleForUrl(videoUrl))
