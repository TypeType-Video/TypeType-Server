package dev.typetype.server.services

object HomeRecommendationSelector {
    fun pick(
        picker: HomeRecommendationPicker,
        wantDiscovery: Boolean,
        forceDiscovery: Boolean,
        forceNovelty: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): HomeRecommendationSelection? {
        val pick = if (wantDiscovery) {
            pickDiscoveryFirst(picker, forceDiscovery, forceNovelty, subIndex, discoveryIndex)
        } else {
            pickSubscriptionFirst(picker, subIndex, discoveryIndex)
        }
        val selection = pick.first ?: return null
        return HomeRecommendationSelection(
            video = selection.video,
            state = selection.state,
            isNovelty = pick.second,
            source = selection.source,
        )
    }

    private fun pickDiscoveryFirst(
        picker: HomeRecommendationPicker,
        forceDiscovery: Boolean,
        forceNovelty: Boolean,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<HomeRecommendationPickState?, Boolean> {
        val discovery = pickDiscovery(picker, discoveryIndex, forceNovelty)
        val discoveryVideo = discovery.first
        if (discoveryVideo != null) {
            return HomeRecommendationPickState(
                video = discoveryVideo,
                state = HomeRecommendationCursorState(
                    subscriptionIndex = subIndex,
                    discoveryIndex = discovery.second,
                    subscriptionRun = 0,
                    preferDiscovery = false,
                ),
                source = picker.sourceOf(discoveryVideo),
            )
                .let { it to true }
        }
        if (forceDiscovery) return null to false
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first ?: return null to false
        return HomeRecommendationPickState(
            video = subscriptionVideo,
            state = HomeRecommendationCursorState(
                subscriptionIndex = subscription.second,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 1,
                preferDiscovery = false,
            ),
            source = picker.sourceOf(subscriptionVideo),
        )
            .let { it to false }
    }

    private fun pickSubscriptionFirst(
        picker: HomeRecommendationPicker,
        subIndex: Int,
        discoveryIndex: Int,
    ): Pair<HomeRecommendationPickState?, Boolean> {
        val subscription = picker.fromSubscriptions(subIndex)
        val subscriptionVideo = subscription.first
        if (subscriptionVideo != null) {
            return HomeRecommendationPickState(
                video = subscriptionVideo,
                state = HomeRecommendationCursorState(
                    subscriptionIndex = subscription.second,
                    discoveryIndex = discoveryIndex,
                    subscriptionRun = 1,
                    preferDiscovery = false,
                ),
                source = picker.sourceOf(subscriptionVideo),
            )
                .let { it to false }
        }
        val discovery = pickDiscovery(picker, discoveryIndex, forceNovelty = false)
        val discoveryVideo = discovery.first ?: return null to false
        return HomeRecommendationPickState(
            video = discoveryVideo,
            state = HomeRecommendationCursorState(
                subscriptionIndex = subIndex,
                discoveryIndex = discovery.second,
                subscriptionRun = 0,
                preferDiscovery = false,
            ),
            source = picker.sourceOf(discoveryVideo),
        )
            .let { it to true }
    }

    private fun pickDiscovery(
        picker: HomeRecommendationPicker,
        discoveryIndex: Int,
        forceNovelty: Boolean,
    ): Pair<dev.typetype.server.models.VideoItem?, Int> {
        val strict = picker.fromDiscovery(discoveryIndex, forceNovelty)
        if (strict.first != null) return strict
        return picker.fromDiscoveryRelaxed(discoveryIndex)
    }
}
