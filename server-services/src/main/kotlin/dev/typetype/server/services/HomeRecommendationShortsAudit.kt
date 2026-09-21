package dev.typetype.server.services

import org.slf4j.LoggerFactory

object HomeRecommendationShortsAudit {
    private val log = LoggerFactory.getLogger("HomeRecommendationMixer")

    fun logComposition(
        userId: String?,
        serviceId: Int?,
        context: HomeRecommendationSessionContext,
        selectedSize: Int,
        discoveryCount: Int,
        subscriptionCount: Int,
        targetDiscoveryRatio: Double,
        discoveryFloorRatio: Double,
        sourceBySelectedUrl: Map<String, HomeRecommendationSourceTag>,
    ) {
        val breakdown = HomeRecommendationSourceTag.entries.joinToString(",") { tag ->
            val count = sourceBySelectedUrl.values.count { it == tag }
            "${tag.name}=$count"
        }
        val selectedRatio = discoveryCount.toDouble() / selectedSize.toDouble()
        log.info(
            "shorts-composition userId=${userId ?: "unknown"} serviceId=${serviceId ?: -1} intent=${context.intent.name.lowercase()} " +
                "pageSize=$selectedSize discovery=$discoveryCount subscriptions=$subscriptionCount " +
                "targetRatio=%.2f floorRatio=%.2f selectedRatio=%.2f breakdown=$breakdown".format(
                    targetDiscoveryRatio,
                    discoveryFloorRatio,
                    selectedRatio,
                ),
        )
    }

    fun tryLog(
        mode: HomeRecommendationPoolMode,
        selectedSize: Int,
        userId: String?,
        serviceId: Int?,
        context: HomeRecommendationSessionContext,
        discoveryCount: Int,
        subscriptionCount: Int,
        targetDiscoveryRatio: Double,
        discoveryFloorRatio: Double,
        sourceBySelectedUrl: Map<String, HomeRecommendationSourceTag>,
    ) {
        if (mode != HomeRecommendationPoolMode.SHORTS || selectedSize == 0) return
        logComposition(
            userId = userId,
            serviceId = serviceId,
            context = context,
            selectedSize = selectedSize,
            discoveryCount = discoveryCount,
            subscriptionCount = subscriptionCount,
            targetDiscoveryRatio = targetDiscoveryRatio,
            discoveryFloorRatio = discoveryFloorRatio,
            sourceBySelectedUrl = sourceBySelectedUrl,
        )
        if (discoveryCount == 0) {
            log.warn(
                "shorts-composition-zero-discovery userId=${userId ?: "unknown"} serviceId=${serviceId ?: -1} intent=${context.intent.name.lowercase()} pageSize=$selectedSize",
            )
        }
    }
}
