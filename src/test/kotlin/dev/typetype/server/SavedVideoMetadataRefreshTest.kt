package dev.typetype.server

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.WatchLaterTable
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.UserVideoMetadataRepairService
import dev.typetype.server.services.VideoMetadataResolver
import dev.typetype.server.services.WatchLaterService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class SavedVideoMetadataRefreshTest {
    private val watchLater = WatchLaterService()

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"
        private const val TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000L

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `old saved metadata is refreshed with the current view count and avatar`() = runBlocking {
        val attempts = AtomicInteger()
        watchLater.add(TEST_USER_ID, savedVideo())
        age(viewCount = 55L, channelAvatar = "")

        runRepair(attempts) { stream(viewCount = 90_000L, avatar = "https://avatar.test/uc1.jpg") }

        val saved = watchLater.getAll(TEST_USER_ID).single()
        assertEquals(1, attempts.get())
        assertEquals(90_000L, saved.viewCount)
        assertEquals("https://avatar.test/uc1.jpg", saved.channelAvatar)
    }

    @Test
    fun `recently saved complete metadata is left alone`() = runBlocking {
        val attempts = AtomicInteger()
        watchLater.add(TEST_USER_ID, savedVideo())

        runRepair(attempts) { stream(viewCount = 90_000L, avatar = "https://avatar.test/uc1.jpg") }

        assertEquals(0, attempts.get())
        assertEquals(55L, watchLater.getAll(TEST_USER_ID).single().viewCount)
    }

    @Test
    fun `partial metadata never erases stored values`() = runBlocking {
        val attempts = AtomicInteger()
        watchLater.add(TEST_USER_ID, savedVideo())
        val agedAt = age(viewCount = 55L, channelAvatar = "https://avatar.test/old.jpg")

        runRepair(attempts) { stream(viewCount = 0L, avatar = "") }

        val saved = watchLater.getAll(TEST_USER_ID).single()
        assertEquals(55L, saved.viewCount)
        assertEquals("https://avatar.test/old.jpg", saved.channelAvatar)
        assertTrue(refreshedAt() > agedAt)
    }

    @Test
    fun `an unavailable video waits for the next refresh instead of being retried`() = runBlocking {
        val attempts = AtomicInteger()
        watchLater.add(TEST_USER_ID, savedVideo())
        val agedAt = age(viewCount = 55L, channelAvatar = "https://avatar.test/uc1.jpg")

        runRepair(attempts) { ExtractionResult.BadRequest("Video unavailable") }

        assertEquals(1, attempts.get())
        assertEquals(55L, watchLater.getAll(TEST_USER_ID).single().viewCount)
        assertTrue(refreshedAt() > agedAt)
    }

    private suspend fun runRepair(attempts: AtomicInteger, answer: () -> ExtractionResult<StreamResponse>) {
        val streamService = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                attempts.incrementAndGet()
                return answer()
            }
        }
        val repair = UserVideoMetadataRepairService(VideoMetadataResolver(streamService))
        val scope = CoroutineScope(SupervisorJob())
        try {
            repair.scheduleWatchLater(scope, TEST_USER_ID)
            withTimeout(10_000) {
                while (scope.coroutineContext.job.children.any()) delay(10)
            }
        } finally {
            scope.cancel()
        }
    }

    private suspend fun age(viewCount: Long, channelAvatar: String): Long {
        val agedAt = System.currentTimeMillis() - TWO_DAYS_MS
        DatabaseFactory.query {
            WatchLaterTable.update({ WatchLaterTable.url eq VIDEO_URL }) {
                it[WatchLaterTable.viewCount] = viewCount
                it[WatchLaterTable.channelAvatar] = channelAvatar
                it[metadataRefreshedAt] = agedAt
            }
        }
        return agedAt
    }

    private suspend fun refreshedAt(): Long = DatabaseFactory.query {
        WatchLaterTable.selectAll().single()[WatchLaterTable.metadataRefreshedAt]
    }

    private fun savedVideo() = WatchLaterItem(
        url = VIDEO_URL, title = "Saved abc123", thumbnail = "https://thumb.test/abc123.jpg", duration = 120L,
        channelName = "Channel", channelUrl = "https://www.youtube.com/channel/UC1",
        channelAvatar = "https://avatar.test/uc1.jpg", viewCount = 55L, publishedAt = 1L,
    )

    private fun stream(viewCount: Long, avatar: String): ExtractionResult<StreamResponse> = ExtractionResult.Success(
        StreamResponse(
            id = "abc123", title = "Resolved abc123", uploaderName = "Channel", uploaderUrl = "https://www.youtube.com/channel/UC1", uploaderAvatarUrl = avatar,
            thumbnailUrl = "https://thumb.test/abc123.jpg", description = "", duration = 120L, viewCount = viewCount, likeCount = 0L, dislikeCount = 0L, uploadDate = "", uploaded = 1L,
            uploaderSubscriberCount = 0L, uploaderVerified = false, category = "", license = "", visibility = "", tags = emptyList(), streamType = "video", isShortFormContent = false,
            requiresMembership = false, startPosition = 0L, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(), audioStreams = emptyList(),
            originalAudioTrackId = null, preferredDefaultAudioTrackId = null, videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(), sponsorBlockSegments = emptyList(), relatedStreams = emptyList(),
        ),
    )
}
