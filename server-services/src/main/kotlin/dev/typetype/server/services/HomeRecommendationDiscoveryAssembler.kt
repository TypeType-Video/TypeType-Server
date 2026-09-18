package dev.typetype.server.services

class HomeRecommendationDiscoveryAssembler {
    fun build(
        profile: HomeRecommendationProfile,
        candidates: List<HomeRecommendationTaggedVideo>,
        explorationCap: Int,
    ): List<HomeRecommendationTaggedVideo> {
        return candidates
            .asSequence()
            .filter { tagged -> tagged.video.uploaderUrl !in profile.subscriptionChannels }
            .filter { tagged -> HomeRecommendationLiveTitleDetector.isLiveLike(tagged.video.title).not() }
            .distinctBy { tagged -> tagged.video.url }
            .take(explorationCap)
            .toList()
    }
}
