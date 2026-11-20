package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.PlaylistVideosTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.eq
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class UserVideoMetadataRepairService(private val resolver: VideoMetadataResolver) {
    private val logger = LoggerFactory.getLogger(UserVideoMetadataRepairService::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastScheduledAt = ConcurrentHashMap<String, Long>()

    fun schedulePlaylist(scope: CoroutineScope, userId: String, playlistId: String): Unit =
        schedule(scope, "playlist:$userId:$playlistId") { excluded -> repairPlaylist(userId, playlistId, excluded) }

    fun scheduleWatchLater(scope: CoroutineScope, userId: String): Unit =
        schedule(scope, "watch-later:$userId") { excluded -> repairWatchLater(userId, excluded) }

    fun scheduleFavorites(scope: CoroutineScope, userId: String): Unit =
        schedule(scope, "favorites:$userId") { excluded -> repairFavorites(userId, excluded) }

    private suspend fun repairPlaylist(userId: String, playlistId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.playlistVideos, PlaylistVideosTable.playlistId eq playlistId)

    private suspend fun repairWatchLater(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.watchLater)

    private suspend fun repairFavorites(userId: String, excluded: Set<String>): RepairOutcome =
        repair(userId, excluded, SavedVideoColumns.favorites)

    private suspend fun drain(key: String, repair: suspend (Set<String>) -> RepairOutcome) {
        val excluded = mutableSetOf<String>()
        var updated = 0
        for (batch in 1..MAX_BATCHES_PER_RUN) {
            val outcome = repair(excluded)
            if (outcome.attempted == 0) break
            excluded += outcome.attemptedUrls
            updated += outcome.updated
            if (batch < MAX_BATCHES_PER_RUN) delay(BATCH_DELAY_MS)
        }
        if (excluded.isNotEmpty()) {
            logger.info("Video metadata refresh {}: {} videos attempted, {} rows updated", key.substringBefore(':'), excluded.size, updated)
        }
    }

    private fun schedule(scope: CoroutineScope, key: String, repair: suspend (Set<String>) -> RepairOutcome) {
        val now = System.currentTimeMillis()
        val lastScheduled = lastScheduledAt[key]
        if (lastScheduled != null && now - lastScheduled < REPAIR_COOLDOWN_MS) return
        if (!inFlight.add(key)) return
        lastScheduledAt[key] = now
        scope.launch(Dispatchers.IO) {
            try {
                drain(key, repair)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.warn("Background video metadata repair failed", error)
            } finally {
                inFlight.remove(key)
            }
        }
    }

    private suspend fun repair(
        userId: String,
        excluded: Set<String>,
        collection: SavedVideoColumns,
        within: Op<Boolean>? = null,
    ): RepairOutcome {
        val now = System.currentTimeMillis()
        val candidates = DatabaseFactory.query { collection.candidateUrls(userId, now, within) }
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
