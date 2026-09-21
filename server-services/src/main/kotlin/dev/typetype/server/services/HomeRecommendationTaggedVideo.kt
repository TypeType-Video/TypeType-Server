package dev.typetype.server.services

import dev.typetype.server.models.VideoItem

data class HomeRecommendationTaggedVideo(
    val video: VideoItem,
    val source: HomeRecommendationSourceTag,
)
