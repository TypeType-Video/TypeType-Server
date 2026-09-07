package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.schabi.newpipe.extractor.localization.Localization
import dev.typetype.server.sabr.YoutubeSabrFormat

internal object SabrAdaptiveInitialization {
    private val localization = Localization("en", "US")

    suspend fun fetch(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        cache: CacheService?,
        timeoutMs: Long = 2_000L,
    ): ByteArray? {
        SabrInitializationData.fetch(holder.key.videoId, format, cache)?.let {
            holder.session.streamState.ingestInitializationData(format, it)
            return it
        }
        val data = fetchRange(holder, format, timeoutMs) ?: return null
        SabrInitializationData.remember(holder.key.videoId, format, data, cache)
        return data
    }

    suspend fun fetchRange(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
        timeoutMs: Long,
    ): ByteArray? {
        val poToken = holder.session.streamState.poToken?.takeIf { it.isNotEmpty() }
            ?: holder.playerContextToken?.streamingPoTokenBytesFor(holder.info)
            ?: return null
        return runCatchingNonCancellation {
            runInterruptible(Dispatchers.IO) {
                holder.withPlayerContext {
                    fetchInitializationData(format, localization, timeoutMs, poToken)
                }
            }
        }.getOrNull()
    }
}
