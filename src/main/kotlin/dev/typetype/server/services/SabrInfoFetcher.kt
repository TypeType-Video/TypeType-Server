package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import dev.typetype.server.sabr.YoutubeSabrClientProfile
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import org.slf4j.LoggerFactory

internal class SabrInfoFetcher(
    private val tokenClient: TypetypeTokenSabrTokenClient,
    private val sessionClient: TypetypeTokenYoutubeSessionClient? = null,
    private val infoCache: SabrPreparedInfoCache = SabrPreparedInfoCache(),
    sharedCache: CacheService? = null,
    private val playerInfoProbe: SabrPlayerInfoProbe = PipePipeSabrPlayerInfoProbe,
) {
    private val repository = SabrInfoRepository(infoCache, sharedCache)
    private val protectedContextRecovery = SabrProtectedContextRecovery(tokenClient)

    suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
        isolatedPlayback: Boolean = false,
    ): SabrPreparedInfo? = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        if (isolatedPlayback) {
            return@withContext fetchInfoOnce(videoId, startTimeMs, isolatedPlayback = true)
                ?.also { logFetch(videoId, startTimeMs, startedAt, "isolated_network") }
        }
        if (cachedFirst) repository.local(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "cache")
            return@withContext it
        }
        if (cachedFirst) tokenClient.fetch(videoId)?.let { token ->
            repository.shared(videoId, token)?.let { prepared ->
                logFetch(videoId, startTimeMs, startedAt, "shared_cache")
                return@withContext prepared
            }
        }
        fetchPlayable(videoId, startTimeMs)?.let {
            logFetch(videoId, startTimeMs, startedAt, "network")
            return@withContext it
        }
        repository.local(videoId, startTimeMs)?.also { logFetch(videoId, startTimeMs, startedAt, "late_cache") }
    }

    private fun logFetch(videoId: String, startTimeMs: Long, startedAt: Long, source: String): Unit {
        logger.info(
            "sabr_info_fetch videoId={} startTimeMs={} source={} elapsedMs={}",
            videoId,
            startTimeMs,
            source,
            System.currentTimeMillis() - startedAt,
        )
    }

    suspend fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit {
        repository.rememberInitialization(videoId, info)
        fetchInfo(videoId, startTimeMs = 0L, cachedFirst = true)
    }

    suspend fun rememberPreparedInfo(videoId: String, prepared: SabrPreparedInfo): Unit {
        repository.rememberInitialization(videoId, prepared.info)
        repository.putPrepared(videoId, startTimeMs = 0L, prepared)
    }

    suspend fun invalidatePlayback(videoId: String): Unit = repository.invalidatePlayback(videoId)

    suspend fun recoverProtectedPlayback(videoId: String, rejectedVisitorData: String?): Unit =
        withContext(Dispatchers.IO) {
            protectedContextRecovery.refreshIfRejected(videoId, rejectedVisitorData)
            repository.invalidatePlayback(videoId)
        }

    fun initializationFormat(videoId: String, target: YoutubeSabrFormat): YoutubeSabrFormat? =
        repository.initializationFormat(videoId, target)

    fun evictExpired(): Unit = repository.evictExpired()

    private suspend fun fetchPlayable(videoId: String, startTimeMs: Long): SabrPreparedInfo? =
        fetchInfoOnce(videoId, startTimeMs)?.let { repository.putPrepared(videoId, startTimeMs, it) }

    private suspend fun fetchInfoOnce(
        videoId: String,
        startTimeMs: Long,
        isolatedPlayback: Boolean = false,
    ): SabrPreparedInfo? =
        withTimeoutOrNull(SabrSessionStoreDefaults.INFO_TIMEOUT_MS) {
            val tokenSession = sessionClient?.fetchPlaybackSession(videoId, isolatedPlayback)
            tokenSession?.preparedSabrInfo()?.let { return@withTimeoutOrNull it }
            val token = tokenClient.fetch(videoId)
                ?: return@withTimeoutOrNull null.also {
                    logger.warn(
                        "sabr_probe event=token_missing videoId={} startTimeMs={}",
                        videoId,
                        startTimeMs,
                    )
                }
            tokenSession
                ?.takeIf { it.info.visitorData == token.visitorData }
                ?.let {
                    SabrPreparedInfo(it.info, token, it.isLive, it.isLiveContent)
                }
                ?.takeIf { it.hasAudioAndVideoFormats() }
                ?.let { return@withTimeoutOrNull it }
            val recovery = SabrPlayerContextRecovery(videoId, token, tokenClient, playerInfoProbe)
            CLIENT_PROFILES.firstNotNullOfOrNull { profile ->
                fetchInfoForProfile(videoId, startTimeMs, profile, recovery)
                    ?.takeIf { it.hasAudioAndVideoFormats() }
            }
        }

    private fun fetchInfoForProfile(
        videoId: String,
        startTimeMs: Long,
        profile: YoutubeSabrClientProfile,
        recovery: SabrPlayerContextRecovery,
    ): SabrPreparedInfo? {
        return when (val result = recovery.fetch(profile)) {
            is SabrPlayerProbeResult.Failure -> null.also {
                logger.warn(
                    "sabr_probe event=fetch_failed videoId={} profile={} startTimeMs={} contextRefreshAttempted={} errorType={} error={}",
                    videoId,
                    profile,
                    startTimeMs,
                    result.contextRefreshAttempted,
                    result.error.javaClass.simpleName,
                    result.error.message,
                    result.error,
                )
            }
            is SabrPlayerProbeResult.Success -> SabrPreparedInfo(result.info, result.token).also { prepared ->
                if (!prepared.hasAudioAndVideoFormats()) logMissingFormats(videoId, profile, result.info)
                if (result.contextRefreshed) {
                    logger.info("sabr_probe event=player_context_refreshed videoId={} profile={}", videoId, profile)
                }
            }
        }
    }

    private fun logMissingFormats(
        videoId: String,
        profile: YoutubeSabrClientProfile,
        info: YoutubeSabrInfo,
    ): Unit {
        logger.warn(
            "sabr_probe event=no_av_formats videoId={} profile={} formatCount={} hasAudio={} hasVideo={} streamingUrl={}",
            videoId,
            profile,
            info.formats.size,
            info.formats.any { it.isAudio },
            info.formats.any { it.isVideo },
            !info.serverAbrStreamingUrl.isNullOrEmpty(),
        )
    }

    private companion object {
        val logger = LoggerFactory.getLogger(SabrInfoFetcher::class.java)
        val CLIENT_PROFILES = listOf(YoutubeSabrClientProfile.WEB, YoutubeSabrClientProfile.MWEB)
    }
}
