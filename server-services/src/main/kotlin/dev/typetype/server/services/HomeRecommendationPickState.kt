package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationPickState(
    val video: VideoItem,
    val state: HomeRecommendationCursorState,
    val source: HomeRecommendationSourceTag,
)
