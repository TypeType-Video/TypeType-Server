package dev.typetype.server.services

import dev.typetype.server.models.YoutubeTakeoutCommitPlan

class YoutubeTakeoutJobRuntimeCache {
    private val plans = mutableMapOf<String, YoutubeTakeoutCommitPlan>()

    fun setPlan(jobId: String, plan: YoutubeTakeoutCommitPlan) {
        plans[jobId] = plan
    }

    fun getPlan(jobId: String): YoutubeTakeoutCommitPlan? = plans[jobId]
}
