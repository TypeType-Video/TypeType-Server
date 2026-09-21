package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationsDebug

object HomeRecommendationShortsDebugInfo {
    fun fromPage(page: HomeRecommendationPage): HomeRecommendationsDebug {
        val itemSources = page.items.associate { item ->
            val source = page.sourceByUrl[item.url] ?: HomeRecommendationSourceTag.DISCOVERY_EXPLORATION
            item.url to source.apiLabel()
        }
        val sourceBreakdown = HomeRecommendationSourceTag.entries.associate { tag ->
            tag.apiLabel() to itemSources.values.count { it == tag.apiLabel() }
        }
        val total = page.items.size.coerceAtLeast(1)
        return HomeRecommendationsDebug(
            itemSources = itemSources,
            sourceBreakdown = sourceBreakdown,
            discoveryRatio = page.discoveryCount.toDouble() / total.toDouble(),
            targetDiscoveryRatio = page.targetDiscoveryRatio,
            discoveryFloorRatio = page.discoveryFloorRatio,
        )
    }
}
