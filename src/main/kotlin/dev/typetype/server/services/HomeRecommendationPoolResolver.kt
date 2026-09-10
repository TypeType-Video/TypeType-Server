package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HomeRecommendationPoolResolver(
    private val dependencies: HomeRecommendationPoolResolverDependencies,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val state = HomeRecommendationPoolResolverState()
    private val poolCache = HomeRecommendationPoolCache(dependencies.cache)

    suspend fun resolve(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool {
        val key = poolCache.key(
            userId = userId,
            serviceId = serviceId,
            mode = mode,
            personalizationEnabled = false,
        )
        val cached = poolCache.read(key)
        if (cached != null) return cached
        val fullBuild = fullBuild(key, userId, serviceId, mode, context)
        schedulePersistence(key, fullBuild)
        val stale = poolCache.readStale(key)
        if (stale != null) return stale
        val fastMode = if (mode == HomeRecommendationPoolMode.SHORTS) {
            HomeRecommendationPoolMode.FAST_SHORTS
        } else {
            HomeRecommendationPoolMode.FAST
        }
        return buildPool(userId, serviceId, fastMode, context)
    }

    private fun fullBuild(
        key: String,
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        context: HomeRecommendationContext,
    ): Deferred<HomeRecommendationPool> {
        state.fullBuilds[key]?.let { return it }
        val created = scope.async {
            val fullMode = if (mode == HomeRecommendationPoolMode.SHORTS) HomeRecommendationPoolMode.SHORTS else HomeRecommendationPoolMode.FULL
            buildPool(userId, serviceId, fullMode, context)
        }
        val winner = state.fullBuilds.putIfAbsent(key, created)
        if (winner != null) {
            created.cancel()
            return winner
        }
        created.invokeOnCompletion { state.fullBuilds.remove(key, created) }
        return created
    }

    private suspend fun buildPool(
        userId: String,
        serviceId: Int,
        mode: HomeRecommendationPoolMode,
        context: HomeRecommendationContext,
    ): HomeRecommendationPool = HomeRecommendationBuilder(
        subscriptionsService = dependencies.subscriptionsService,
        subscriptionFeedService = dependencies.subscriptionFeedService,
        subscriptionShortsFeedService = dependencies.subscriptionShortsFeedService,
        historyService = dependencies.historyService,
        favoritesService = dependencies.favoritesService,
        watchLaterService = dependencies.watchLaterService,
        blockedService = dependencies.blockedService,
        streamService = dependencies.streamService,
        trendingService = dependencies.trendingService,
    ).build(
        userId = userId,
        serviceId = serviceId,
        mode = mode,
        context = context,
    )

    private fun schedulePersistence(key: String, build: Deferred<HomeRecommendationPool>) {
        if (!state.pendingPersistence.add(key)) return
        scope.launch {
            runCatching { build.await() }.getOrNull()?.let { poolCache.write(key, it) }
            state.pendingPersistence.remove(key)
        }
    }

    override fun close() {
        scope.cancel()
    }

}
