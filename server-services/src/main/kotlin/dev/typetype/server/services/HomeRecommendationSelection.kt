package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationSelection(
    val video: VideoItem,
    val state: HomeRecommendationCursorState,
    val isNovelty: Boolean,
    val source: HomeRecommendationSourceTag,
)
