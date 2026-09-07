package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.time.Duration
import java.time.Instant

internal class SabrSessionStore(
    tokenServiceUrl: String,
    private val maxSessions: Int = SabrSessionStoreDefaults.maxSessions(),
    private val idleEviction: Duration = SabrSessionStoreDefaults.idleEviction(),
    private val pumpLoopIntervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
    private val tokenClient: TypetypeTokenSabrTokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl),
    private val sessionClient: TypetypeTokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(tokenServiceUrl),
    internal val initCache: CacheService? = null,
) {
    private val registry = SabrSessionRegistry()
    private val segmentCache = SabrSegmentCache()
    private val pump = SabrSessionPump(segmentCache) { holder ->
        val binding = holder.playerContextToken?.sessionBinding
        if (binding == null) tokenClient.fetch(holder.key.videoId, refreshVideo = true)
        else tokenClient.fetchSession(holder.key.videoId, binding, refreshVideo = true)
    }
    private val warmer = SabrPlaybackWarmer()
    private val infoFetcher = SabrInfoFetcher(tokenClient, sessionClient, sharedCache = initCache)
    private val sessionFactory = SabrSessionFactory(tokenClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleCheckJob: Job = scope.launch { idleEvictionLoop() }

    init {
        runCatching { NewPipeInitializer.init() }
    }
    internal fun getOrCreate(
        videoId: String,
        userId: String,
        info: YoutubeSabrInfo,
        audioFormat: YoutubeSabrFormat,
        videoFormat: YoutubeSabrFormat,
        initialToken: SabrTokenBundle? = null,
        startTimeMs: Long = 0L,
        startPump: Boolean = true,
        purpose: SabrSessionPurpose = SabrSessionPurpose.MANIFEST,
        audioOnly: Boolean = false,
        initialGeneration: Long = 0L,
        source: SabrPreparedSource = SabrPreparedSource.PUBLIC,
    ): SabrSessionHolder {
        val sessionToken = SabrSessionTokenGenerator.newToken()
        val isolatedSourceId = sessionToken.takeIf {
            purpose == SabrSessionPurpose.PLAYBACK || purpose == SabrSessionPurpose.DOWNLOAD
        }
        val key = SabrSessionKey(
            videoId,
            userId,
            audioFormat.itag,
            audioFormat.audioTrackId,
            videoFormat.itag,
            startTimeMs.coerceAtLeast(0L),
            purpose,
            audioOnly,
            isolatedSourceId,
        )
        registry.getReusable(key)?.let { return it }
        registry.ensureCapacity(maxSessions)
        val holder = sessionFactory.create(
            key,
            info,
            audioFormat,
            videoFormat,
            sessionToken,
            initialToken,
            initialGeneration,
            source,
        )
        val active = registry.put(key, holder)
        if (active !== holder) return active
        registry.trimToCapacity(maxSessions, holder)
        if (startPump) startPump(holder)
        return holder
    }
    internal fun startPump(holder: SabrSessionHolder) {
        scope.launchSabrPump(pump, registry, holder, pumpLoopIntervalMs)
    }

    internal fun warmPlaybackAsync(holder: SabrSessionHolder) {
        if (holder.playerTimeMs() > 0L) return
        if (holder.playbackState() == SabrPlaybackState.REQUESTING || holder.playbackState() == SabrPlaybackState.REPOSITIONING) return
        scope.launch { fetchMediaAt(holder, holder.playerTimeMs()) }
    }

    internal fun warmInitializationAsync(holder: SabrSessionHolder): Unit =
        SabrInitializationPolicy.warmFormats(holder.key.audioOnly, holder.audioFormat, holder.videoFormat).forEach { format ->
            scope.launch { fetchDirectInitialization(holder, format) }
        }

    internal fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): SabrSessionHolder? =
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag, 0L))

    internal fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    internal fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? = registry.lookupByToken(videoId, token, itag)

    internal fun lookupByToken(videoId: String, token: String): SabrSessionHolder? = registry.lookupByToken(videoId, token)

    internal fun lookupByToken(token: String): SabrSessionHolder? = registry.lookupByToken(token)

    internal suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8): Unit = pump.ensureWarmed(holder, maxPumps)

    internal suspend fun preflightPlayback(holder: SabrSessionHolder, playerTimeMs: Long): Boolean =
        warmer.preflight(this, holder, playerTimeMs)

    internal suspend fun cachedMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<CachedSabrSegment>? =
        holder.mediaRequestsAt(playerTimeMs).map { segmentCache.get(holder, it) }
            .takeIf { cached -> cached.all { it != null } }?.filterNotNull()

    internal suspend fun cachedSegment(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? {
        holder.session.getCachedSegment(request)?.let {
            segmentCache.put(holder, it)
            holder.clearSegmentDemand(request)
            return segmentCache.get(holder, request)
        }
        return segmentCache.get(holder, request)?.also { holder.clearSegmentDemand(request) }
    }

    internal fun requestSegmentDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        generation: Long,
    ): Unit {
        holder.requestSegmentDemand(request, generation)
        startPump(holder)
    }

    internal suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
        isolatedPlayback: Boolean = false,
    ): SabrPreparedInfo? = infoFetcher.fetchInfo(videoId, startTimeMs, cachedFirst, isolatedPlayback)

    internal suspend fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit =
        infoFetcher.rememberExtractedInfo(videoId, info)
    internal suspend fun rememberPreparedInfo(videoId: String, prepared: SabrPreparedInfo): Unit = infoFetcher.rememberPreparedInfo(videoId, prepared)
    internal suspend fun invalidatePlaybackInfo(videoId: String): Unit = infoFetcher.invalidatePlayback(videoId)

    internal suspend fun recoverProtectedPlaybackInfo(holder: SabrSessionHolder): Unit =
        infoFetcher.recoverProtectedPlayback(
            holder.key.videoId,
            holder.playerContextToken?.visitorData ?: holder.info.visitorData,
        )

    internal fun refreshVideoPoToken(videoId: String): SabrTokenBundle? =
        tokenClient.fetch(videoId, refreshVideo = true)

    internal suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = pump.fetchSegment(holder, request)

    internal suspend fun fetchMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? =
        pump.fetchMediaAt(holder, playerTimeMs)

    internal suspend fun fetchInitializationData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        if (SabrInitializationPolicy.requiresVideoFirst(holder.key.audioOnly, format.isAudio, holder.playerTimeMs())) {
            fetchInitializationSegmentData(holder, holder.videoFormat)
        }
        return fetchInitializationSegmentData(holder, format)
    }

    private suspend fun fetchInitializationSegmentData(
        holder: SabrSessionHolder,
        format: YoutubeSabrFormat,
    ): ByteArray? {
        holder.liveInitialization(format)?.let { return it }
        val request = SabrSegmentRequest.initialization(format)
        holder.session.getCachedSegment(request)?.let { segmentCache.put(holder, it); return it.data }
        SabrAdaptiveInitialization.fetch(holder, format, initCache)?.let { return it }
        SabrInitializationData.bootstrap(holder, format, initCache)?.let { return it }
        val segment = pump.fetchSegment(holder, request) ?: return null
        segmentCache.put(holder, segment)
        SabrInitializationData.remember(holder.key.videoId, format, segment.data, initCache)
        return segment.data
    }

    private suspend fun fetchDirectInitialization(holder: SabrSessionHolder, format: YoutubeSabrFormat): Unit {
        val data = SabrInitializationData.fetch(holder.key.videoId, format, initCache) ?: return
        holder.pumpMutex.withLock { holder.session.streamState.ingestInitializationData(format, data) }
    }

    fun release() {
        idleCheckJob.cancel()
        scope.cancel()
        registry.clear()
    }

    internal fun release(holder: SabrSessionHolder): Unit = registry.remove(holder)

    private suspend fun idleEvictionLoop() {
        while (true) {
            delay(15_000)
            registry.evictIdle(Instant.now().minus(idleEviction))
            infoFetcher.evictExpired()
            SabrInitializationData.evictExpired()
        }
    }
}
