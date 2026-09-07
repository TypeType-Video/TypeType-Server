package dev.typetype.server.services

import kotlinx.coroutines.sync.Mutex
import dev.typetype.server.sabr.SabrMediaSegment
import dev.typetype.server.sabr.SabrSegmentRequest
import dev.typetype.server.sabr.YoutubeSabrFormat
import dev.typetype.server.sabr.YoutubeSabrInfo
import dev.typetype.server.sabr.YoutubeSabrSession
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class SabrSessionHolder(
    val session: YoutubeSabrSession,
    val info: YoutubeSabrInfo,
    val audioFormat: YoutubeSabrFormat,
    val videoFormat: YoutubeSabrFormat,
    val sessionToken: String,
    val key: SabrSessionKey,
    @Volatile var lastRequestAt: Instant,
    @Volatile var playerContextToken: SabrTokenBundle? = null,
    val pumpMutex: Mutex = Mutex(),
    initialGeneration: Long = 0L,
    val source: SabrPreparedSource = SabrPreparedSource.PUBLIC,
) {
    private val readerPositions = ConcurrentHashMap<SabrReaderTrackKey, Long>()
    private val lastServedSequences = ConcurrentHashMap<SabrReaderTrackKey, Int>()
    private val activeItags: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val observedMediaAnchors = ConcurrentHashMap<Int, SabrMediaSegment>()
    private val earliestObservedMediaStarts = ConcurrentHashMap<Int, Long>()
    private val liveInitializationData = ConcurrentHashMap<Int, ByteArray>()
    private val pendingRefetch = AtomicReference<SabrSegmentRequest?>()
    private val pendingForwardSeek = AtomicReference<SabrSegmentRequest?>()
    internal val pumpCoordinator = SabrPumpCoordinator()
    private val unauthorizedRefreshAttempted = AtomicBoolean(false)
    private val expectedLive = AtomicBoolean(false)
    private val activeGeneration = AtomicLong(initialGeneration.coerceAtLeast(0L))
    private val segmentMemory = SabrMemorySegmentCache(SABR_MAX_SEGMENT_MEMORY_BYTES)
    private val playbackStatus = SabrPlaybackStatus()
    private val requestedSeekTimeMs = AtomicLong(-1L)
    @Volatile private var playerTimeMs: Long = 0L
    @Volatile private var playbackRate = 1.0f
    init { setActiveTracks(videoActive = true, audioActive = true) }
    fun activeGeneration(): Long = activeGeneration.get()

    fun advancePlaybackGeneration(ms: Long): Long {
        setPlayerTimeMs(ms)
        val generation = activeGeneration.incrementAndGet()
        clearReaderStateBefore(generation)
        pendingRefetch.set(null)
        pendingForwardSeek.set(null)
        for (itag in activeItags) {
            readerPositions[SabrReaderTrackKey(generation, itag)] = playerTimeMs()
        }
        return generation
    }

    fun setReaderPosition(format: YoutubeSabrFormat, endMs: Long): Unit =
        setReaderPosition(format, endMs, activeGeneration())

    fun setReaderPosition(format: YoutubeSabrFormat, endMs: Long, generation: Long): Unit {
        if (generation == activeGeneration()) readerPositions[SabrReaderTrackKey(generation, format.itag)] = endMs
    }

    fun touch(now: Instant = Instant.now()): Unit {
        lastRequestAt = now
    }

    fun markUnauthorizedRefreshAttempted(): Boolean = unauthorizedRefreshAttempted.compareAndSet(false, true)

    fun markExpectedLive(): Unit {
        expectedLive.set(true)
    }

    fun expectsLive(): Boolean = expectedLive.get()

    fun observeMediaSegment(segment: SabrMediaSegment): Unit {
        val header = segment.header
        if (header.isInitSegment || header.sequenceNumber <= 0 ||
            (header.itag != audioFormat.itag && header.itag != videoFormat.itag)
        ) return
        if (header.startMs >= 0L) earliestObservedMediaStarts.merge(header.itag, header.startMs, ::minOf)
        observedMediaAnchors.compute(header.itag) { _, current ->
            if (current == null || header.startMs >= current.header.startMs) segment else current
        }
    }

    fun observedMediaSegment(format: YoutubeSabrFormat): SabrMediaSegment? = observedMediaAnchors[format.itag]
    fun earliestObservedMediaStartMs(format: YoutubeSabrFormat): Long? = earliestObservedMediaStarts[format.itag]

    fun rememberLiveInitialization(itag: Int, data: ByteArray): Unit {
        liveInitializationData.putIfAbsent(itag, data)
    }

    fun liveInitialization(format: YoutubeSabrFormat): ByteArray? = liveInitializationData[format.itag]

    fun setLastServedSequence(itag: Int, sequence: Int): Unit =
        setLastServedSequence(itag, sequence, activeGeneration())

    fun setLastServedSequence(itag: Int, sequence: Int, generation: Long): Unit {
        if (generation == activeGeneration()) lastServedSequences[SabrReaderTrackKey(generation, itag)] = sequence
    }

    fun cachedSegment(key: String): CachedSabrSegment? = segmentMemory.get(key)

    fun putCachedSegment(key: String, segment: CachedSabrSegment): Unit = segmentMemory.put(key, segment)

    fun evictCachedSegmentsBefore(ms: Long): Unit = segmentMemory.evictBefore(ms)

    fun lastServedSequence(format: YoutubeSabrFormat): Int? = lastServedSequence(format, activeGeneration())

    fun lastServedSequence(format: YoutubeSabrFormat, generation: Long): Int? =
        lastServedSequences[SabrReaderTrackKey(generation, format.itag)]

    fun setPlayerTimeMs(ms: Long): Unit {
        playerTimeMs = ms.coerceAtLeast(0L)
    }

    fun playerTimeMs(): Long = playerTimeMs

    fun setPlaybackRate(rate: Float): Unit {
        playbackRate = rate
        session.streamState.setPlaybackRate(rate)
    }

    fun playbackRate(): Float = playbackRate

    fun setRequestedSeekTimeMs(ms: Long): Unit {
        requestedSeekTimeMs.set(ms.coerceAtLeast(0L))
    }

    fun anchorReaderPositions(ms: Long, generation: Long = activeGeneration()): Unit {
        for (itag in activeItags) readerPositions[SabrReaderTrackKey(generation, itag)] = ms.coerceAtLeast(0L)
    }

    fun requestedSeekTimeMs(): Long? = requestedSeekTimeMs.get().takeIf { it >= 0L }

    fun isAudioActive(): Boolean = activeItags.contains(audioFormat.itag)

    fun isVideoActive(): Boolean = activeItags.contains(videoFormat.itag)

    fun readerHeadMs(): Long {
        var head = 0L
        val generation = activeGeneration()
        for (itag in activeItags) {
            head = maxOf(head, readerPositions[SabrReaderTrackKey(generation, itag)] ?: 0L)
        }
        return head
    }

    fun readerPosition(format: YoutubeSabrFormat): Long? = readerPosition(format, activeGeneration())

    fun readerPosition(format: YoutubeSabrFormat, generation: Long): Long? =
        readerPositions[SabrReaderTrackKey(generation, format.itag)]

    fun readerTailMs(): Long {
        if (activeItags.isEmpty()) return 0L
        var tail = Long.MAX_VALUE
        val generation = activeGeneration()
        for (itag in activeItags) {
            val position = readerPositions[SabrReaderTrackKey(generation, itag)] ?: return 0L
            tail = minOf(tail, position)
        }
        return if (tail == Long.MAX_VALUE) 0L else tail
    }

    fun requestRefetch(request: SabrSegmentRequest): Unit {
        pendingRefetch.set(request)
    }

    fun requestForwardSeek(request: SabrSegmentRequest): Unit {
        pendingForwardSeek.set(request)
    }

    fun consumeRefetch(): SabrSegmentRequest? = pendingRefetch.getAndSet(null)

    fun consumeForwardSeek(): SabrSegmentRequest? = pendingForwardSeek.getAndSet(null)

    fun pendingRefetchRequest(): SabrSegmentRequest? = pendingRefetch.get()

    fun pendingForwardSeekRequest(): SabrSegmentRequest? = pendingForwardSeek.get()

    fun hasPendingSeek(): Boolean = pendingRefetch.get() != null || pendingForwardSeek.get() != null

    fun setPlaybackState(state: SabrPlaybackState): Unit = playbackStatus.transition(state)

    fun playbackState(): SabrPlaybackState = playbackStatus.state()

    fun recordNetworkFailure(message: String?): Unit = playbackStatus.recordNetworkFailure(message)

    fun networkFailure(): String? = playbackStatus.networkFailure()

    fun failTerminal(message: String?): Unit = playbackStatus.failTerminal(message)

    fun terminalFailure(): String? = playbackStatus.terminalFailure()

    fun setActiveTracks(videoActive: Boolean, audioActive: Boolean): Unit {
        setActive(videoFormat.itag, videoActive)
        setActive(audioFormat.itag, audioActive)
        session.streamState.setActiveTrackTypes(videoActive, audioActive)
    }

    private fun setActive(itag: Int, active: Boolean): Unit {
        if (active) {
            activeItags.add(itag)
        } else {
            activeItags.remove(itag)
            readerPositions.keys.removeIf { it.itag == itag }
            lastServedSequences.keys.removeIf { it.itag == itag }
        }
    }

    private fun clearReaderStateBefore(generation: Long): Unit {
        readerPositions.keys.removeIf { it.generation < generation }
        lastServedSequences.keys.removeIf { it.generation < generation }
    }
}
