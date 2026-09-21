package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import kotlinx.coroutines.Deferred
import java.util.concurrent.ConcurrentHashMap

class HomeRecommendationPoolResolverState {
    val fullBuilds = ConcurrentHashMap<String, Deferred<HomeRecommendationPool>>()
    val pendingPersistence = ConcurrentHashMap.newKeySet<String>()
}
