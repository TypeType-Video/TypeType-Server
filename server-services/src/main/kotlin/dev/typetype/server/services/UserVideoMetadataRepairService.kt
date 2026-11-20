package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class UserVideoMetadataRepairService(private val resolver: VideoMetadataResolver) {
    private val logger = LoggerFactory.getLogger(UserVideoMetadataRepairService::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastScheduledAt = ConcurrentHashMap<String, Long>()

    fun schedulePlaylists(scope: CoroutineScope, userId: String): Unit = schedule(scope, "playlists:$userId") {
        drain { excluded -> repairPlaylists(userId, excluded) }
    }

    fun scheduleWatchLater(scope: CoroutineScope, userId: String): Unit = schedule(scope, "watch-later:$userId") {
        drain { excluded -> repairWatchLater(userId, excluded) }
    }

    fun scheduleFavorites(scope: CoroutineScope, userId: String): Unit = schedule(scope, "favorites:$userId") {
        drain { excluded -> repairFavorites(userId, excluded) }
    }

    private suspend fun repairPlaylists(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.playlistVideos)

    private suspend fun repairWatchLater(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.watchLater)

    private suspend fun repairFavorites(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.favorites)

    private suspend fun drain(repair: suspend (Set<String>) -> RepairOutcome) {
        val excluded = mutableSetOf<String>()
        repeat(MAX_BATCHES_PER_RUN) {
            val outcome = repair(excluded)
            if (outcome.attempted == 0) return
            excluded += outcome.attemptedUrls
            delay(BATCH_DELAY_MS)
        }
    }

    private fun schedule(scope: CoroutineScope, key: String, repair: suspend () -> Unit) {
        val now = System.currentTimeMillis()
        val lastScheduled = lastScheduledAt[key]
        if (lastScheduled != null && now - lastScheduled < REPAIR_COOLDOWN_MS) return
        if (!inFlight.add(key)) return
        lastScheduledAt[key] = now
        scope.launch(Dispatchers.IO) {
            try {
                repair()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.warn("Background video metadata repair failed", error)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun repair(userId: String, excluded: Set<String>, collection: SavedVideoColumns): RepairOutcome {
        val now = System.currentTimeMillis()
        val candidates = DatabaseFactory.query { collection.candidateUrls(userId, now) }
        val urls = candidates.filterNot { it in excluded }.take(MAX_REPAIR_PER_REQUEST)
        if (urls.isEmpty()) return RepairOutcome(attemptedUrls = emptySet(), updated = 0)
        val first = resolver.resolveDetailed(urls)
        var updated = applyMetadata(userId, first)
        if (first.retryableUrls.isNotEmpty()) {
            delay(RETRY_DELAY_MS)
            val retried = resolver.resolveDetailed(first.retryableUrls)
            updated += applyMetadata(userId, retried)
        }
        val unavailable = urls.toSet() - first.metadata.keys - first.retryableUrls
        if (unavailable.isNotEmpty()) {
            DatabaseFactory.query { SavedVideoColumns.all.forEach { it.touch(userId, unavailable, now) } }
        }
        return RepairOutcome(attemptedUrls = urls.toSet(), updated = updated)
    }

    private suspend fun applyMetadata(userId: String, resolution: VideoMetadataResolution): Int {
        val now = System.currentTimeMillis()
        return DatabaseFactory.query {
            resolution.metadata.values.sumOf { item -> SavedVideoColumns.all.sumOf { it.apply(userId, item, now) } }
        }
    }

    private companion object {
        const val MAX_REPAIR_PER_REQUEST = 8
        const val MAX_BATCHES_PER_RUN = 3
        const val BATCH_DELAY_MS = 1_500L
        const val RETRY_DELAY_MS = 3_000L
        const val REPAIR_COOLDOWN_MS = 5 * 60 * 1000L
    }
}

private data class RepairOutcome(
    val attemptedUrls: Set<String>,
    val updated: Int,
) {
    val attempted: Int get() = attemptedUrls.size
}
