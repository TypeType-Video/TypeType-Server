package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationScoredVideo(
    val video: VideoItem,
    val score: Double,
    val source: HomeRecommendationSourceTag,
)
