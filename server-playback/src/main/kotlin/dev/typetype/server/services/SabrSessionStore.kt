package dev.typetype.server.services

import dev.typetype.server.cache.CacheService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.time.Duration
import java.time.Instant

class SabrSessionStore(
    tokenServiceUrl: String,
    private val maxSessions: Int = SabrSessionStoreDefaults.maxSessions(),
    private val idleEviction: Duration = SabrSessionStoreDefaults.idleEviction(),
    private val pumpLoopIntervalMs: Long = SabrPumpPolicy.IDLE_POLL_MS,
    private val tokenClient: TypetypeTokenSabrTokenClient = TypetypeTokenSabrTokenClient(tokenServiceUrl),
    private val sessionClient: TypetypeTokenYoutubeSessionClient = TypetypeTokenYoutubeSessionClient(tokenServiceUrl),
    internal val initCache: CacheService? = null,
    val sessionEpoch: ProviderSessionEpoch = tokenClient.sessionEpoch,
    private val egressRotation: GluetunEgressRotation? = null,
) {
    private val registry = SabrSessionRegistry()
    private val segmentCache = SabrSegmentCache()
    private val pump = SabrSessionPump(segmentCache, ::reportProtectionPending) { holder ->
        val binding = holder.playerContextToken?.sessionBinding
        if (binding == null) tokenClient.fetch(holder.key.videoId, refreshVideo = true)
        else tokenClient.fetchSession(holder.key.videoId, binding, refreshVideo = true)
    }
    private val warmer = SabrPlaybackWarmer()
    private val infoFetcher = SabrInfoFetcher(
        tokenClient,
        sessionClient,
        sharedCache = initCache,
        sessionEpoch = sessionEpoch,
    )
    private val sessionFactory = SabrSessionFactory(tokenClient)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val idleCheckJob: Job = scope.launch { idleEvictionLoop() }

    private val exitCheck = SabrEgressExitCheck(this)
    private val acceptance = TokenSessionAcceptance(exitCheck, egressRotation, scope)

    init {
        runCatching { NewPipeInitializer.init() }
        egressRotation?.exitCheck = exitCheck
    }
    fun getOrCreate(
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
        val activeCutoff = Instant.now().minus(idleEviction)
        registry.ensureCapacity(maxSessions, activeCutoff)
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
        registry.trimToCapacity(maxSessions, holder, activeCutoff)
        if (startPump) startPump(holder)
        return holder
    }
    fun startPump(holder: SabrSessionHolder) {
        scope.launchSabrPump(pump, registry, holder, pumpLoopIntervalMs)
    }
    fun warmPlaybackAsync(holder: SabrSessionHolder) {
        if (holder.playerTimeMs() > 0L) return
        if (holder.playbackState() == SabrPlaybackState.REQUESTING || holder.playbackState() == SabrPlaybackState.REPOSITIONING) return
        scope.launch { fetchMediaAt(holder, holder.playerTimeMs()) }
    }

    fun warmInitializationAsync(holder: SabrSessionHolder): Unit =
        SabrInitializationPolicy.warmFormats(holder.key.audioOnly, holder.audioFormat, holder.videoFormat).forEach { format ->
            scope.launch { fetchDirectInitialization(holder, format) }
        }

    fun lookup(videoId: String, userId: String, audioItag: Int, videoItag: Int): SabrSessionHolder? =
        registry.get(SabrSessionKey(videoId, userId, audioItag, null, videoItag, 0L))

    fun lookupByItag(videoId: String, userId: String, itag: Int): SabrSessionHolder? =
        registry.lookupByItag(videoId, userId, itag)

    fun lookupByToken(videoId: String, token: String, itag: Int): SabrSessionHolder? = registry.lookupByToken(videoId, token, itag)
    fun lookupByToken(videoId: String, token: String): SabrSessionHolder? = registry.lookupByToken(videoId, token)
    fun lookupByToken(token: String): SabrSessionHolder? = registry.lookupByToken(token)

    suspend fun ensureWarmed(holder: SabrSessionHolder, maxPumps: Int = 8): Unit = pump.ensureWarmed(holder, maxPumps)

    suspend fun preflightPlayback(holder: SabrSessionHolder, playerTimeMs: Long): Boolean =
        warmer.preflight(this, holder, playerTimeMs)

    suspend fun cachedMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<CachedSabrSegment>? =
        holder.mediaRequestsAt(playerTimeMs).map { segmentCache.get(holder, it) }
            .takeIf { cached -> cached.all { it != null } }?.filterNotNull()

    suspend fun cachedSegment(holder: SabrSessionHolder, request: SabrSegmentRequest): CachedSabrSegment? {
        holder.touch()
        holder.session.getCachedSegment(request)?.let {
            segmentCache.put(holder, it)
            holder.clearSegmentDemand(request)
            return segmentCache.get(holder, request)
        }
        return segmentCache.get(holder, request)?.also { holder.clearSegmentDemand(request) }
    }

    fun requestSegmentDemand(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
        generation: Long,
    ): Unit {
        holder.requestSegmentDemand(request, generation)
        startPump(holder)
    }

    suspend fun fetchInfo(
        videoId: String,
        startTimeMs: Long = 0L,
        cachedFirst: Boolean = false,
        isolatedPlayback: Boolean = false,
    ): SabrPreparedInfo? = infoFetcher.fetchInfo(videoId, startTimeMs, cachedFirst, isolatedPlayback)
    suspend fun fetchAudioOnlyMediaInfo(videoId: String): SabrPreparedInfo? = infoFetcher.fetchAudioOnlyMediaInfo(videoId)
    fun rememberExtractedInfoAsync(videoId: String, info: YoutubeSabrInfo): Unit {
        scope.launch { infoFetcher.rememberExtractedInfo(videoId, info) }
    }
    suspend fun rememberExtractedInfo(videoId: String, info: YoutubeSabrInfo): Unit =
        infoFetcher.rememberExtractedInfo(videoId, info)
    suspend fun rememberPreparedInfo(videoId: String, prepared: SabrPreparedInfo): Unit = infoFetcher.rememberPreparedInfo(videoId, prepared)
    suspend fun invalidatePlaybackInfo(videoId: String): Unit = infoFetcher.invalidatePlayback(videoId)
    fun hasPreparedInfo(videoId: String): Boolean = infoFetcher.hasPreparedInfo(videoId)

    /**
     * The token service ignores a forced refresh for a while after its last one, so this asks again
     * until the session generation changes: a check run on the previous session would judge the
     * previous exit.
     */
    suspend fun renewTokenSession(videoId: String) {
        val previous = withContext(Dispatchers.IO) { tokenClient.fetch(videoId) }?.generation
        withTimeoutOrNull(TOKEN_RENEWAL_WAIT_MS) {
            while (withContext(Dispatchers.IO) { tokenClient.fetch(videoId, forceRefresh = true) }?.generation == previous) {
                delay(TOKEN_RENEWAL_RETRY_MS)
            }
        }
        infoFetcher.invalidatePlayback(videoId)
    }

    private fun reportProtectionPending(holder: SabrSessionHolder) {
        if (holder.key.userId == SabrEgressExitCheck.PROBE_USER) return
        acceptance.onHold("sabr_attestation_pending", holder.key.videoId)
    }

    suspend fun recoverProtectedPlaybackInfo(holder: SabrSessionHolder) {
        acceptance.onHold("sabr_attestation", holder.key.videoId)
        infoFetcher.recoverProtectedPlayback(
            holder.key.videoId,
            holder.playerContextToken?.visitorData ?: holder.info.visitorData,
        )
    }

    fun refreshVideoPoToken(videoId: String): SabrTokenBundle? =
        tokenClient.fetch(videoId, refreshVideo = true)

    suspend fun fetchSegment(
        holder: SabrSessionHolder,
        request: SabrSegmentRequest,
    ): SabrMediaSegment? = holder.also { it.touch() }.let { pump.fetchSegment(it, request) }

    suspend fun fetchMediaAt(holder: SabrSessionHolder, playerTimeMs: Long): List<SabrMediaSegment>? =
        holder.also { it.touch() }.let { pump.fetchMediaAt(it, playerTimeMs) }
    suspend fun fetchInitializationData(
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

    fun release(holder: SabrSessionHolder): Unit = registry.remove(holder)

    private suspend fun idleEvictionLoop() {
        while (true) {
            delay(15_000)
            registry.evictIdle(Instant.now().minus(idleEviction))
            infoFetcher.evictExpired()
            SabrInitializationData.evictExpired()
        }
    }

    private companion object {
        const val TOKEN_RENEWAL_WAIT_MS = 45_000L
        const val TOKEN_RENEWAL_RETRY_MS = 5_000L
    }
}
