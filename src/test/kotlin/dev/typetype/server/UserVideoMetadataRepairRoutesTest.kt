package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.PlaylistItem
import dev.typetype.server.models.PlaylistVideoItem
import dev.typetype.server.models.StreamResponse
import dev.typetype.server.models.WatchLaterItem
import dev.typetype.server.routes.favoritesRoutes
import dev.typetype.server.routes.playlistRoutes
import dev.typetype.server.routes.watchLaterRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.FavoritesService
import dev.typetype.server.services.PlaylistService
import dev.typetype.server.services.StreamService
import dev.typetype.server.services.UserVideoMetadataRepairService
import dev.typetype.server.services.VideoMetadataResolver
import dev.typetype.server.services.WatchLaterService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class UserVideoMetadataRepairRoutesTest {
    private val playlists = PlaylistService()
    private val watchLater = WatchLaterService()
    private val favorites = FavoritesService()
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object {
        private const val VIDEO_URL = "https://www.youtube.com/watch?v=abc123"

        @BeforeAll
        @JvmStatic
        fun initDb() = TestDatabase.setup()
    }

    @BeforeEach
    fun clean() = TestDatabase.truncateAll()

    @Test
    fun `collection routes return before fallback metadata repair completes`() = testApplication {
        val repairStarted = CompletableDeferred<Unit>()
        val releaseRepair = CompletableDeferred<Unit>()
        val repair = UserVideoMetadataRepairService(VideoMetadataResolver(fakeStreamService(repairStarted, releaseRepair)))
        val playlist = playlists.create(TEST_USER_ID, PlaylistItem(name = "Imported", description = ""))
        playlists.addVideo(TEST_USER_ID, playlist.id, fallbackVideo(VIDEO_URL))
        watchLater.add(TEST_USER_ID, WatchLaterItem(url = VIDEO_URL, title = "YouTube video abc123", thumbnail = "https://i.ytimg.com/vi/abc123/hqdefault.jpg", duration = 0L))
        favorites.add(TEST_USER_ID, VIDEO_URL)
        application {
            install(ContentNegotiation) { json() }
            routing {
                playlistRoutes(playlists, auth, repair)
                watchLaterRoutes(watchLater, auth, repair)
                favoritesRoutes(favorites, auth, repair)
            }
        }

        val playlistsBody = client.get("/playlists") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()
        assertTrue(playlistsBody.contains("\"videoCount\":1"))
        assertFalse(repairStarted.isCompleted)

        val (playlistBody, watchLaterBody, favoritesBody) = withTimeout(1_000) {
            listOf(
                client.get("/playlists/${playlist.id}") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText(),
                client.get("/watch-later") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText(),
                client.get("/favorites") { header(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText(),
            )
        }

        assertTrue(playlistBody.contains("YouTube video abc123"))
        assertTrue(watchLaterBody.contains("YouTube video abc123"))
        assertFalse(playlistBody.contains("Resolved abc123"))
        assertFalse(watchLaterBody.contains("Resolved abc123"))
        assertFalse(favoritesBody.contains("Resolved abc123"))

        withTimeout(5_000) { repairStarted.await() }
        releaseRepair.complete(Unit)
        withTimeout(5_000) {
            while (
                playlists.getById(TEST_USER_ID, playlist.id)?.videos?.single()?.title != "Resolved abc123" ||
                watchLater.getAll(TEST_USER_ID).single().title != "Resolved abc123" ||
                favorites.getAll(TEST_USER_ID).single().title != "Resolved abc123"
            ) {
                delay(10)
            }
        }
    }

    @Test
    fun `repeated requests coalesce failed metadata repairs during cooldown`() = runBlocking {
        val attempts = AtomicInteger()
        val repairStarted = CompletableDeferred<Unit>()
        val releaseRepair = CompletableDeferred<Unit>()
        val streamService = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                attempts.incrementAndGet()
                repairStarted.complete(Unit)
                releaseRepair.await()
                return ExtractionResult.Failure("Unavailable")
            }
        }
        val repair = UserVideoMetadataRepairService(VideoMetadataResolver(streamService))
        val playlist = playlists.create(TEST_USER_ID, PlaylistItem(name = "Imported", description = ""))
        playlists.addVideo(TEST_USER_ID, playlist.id, fallbackVideo(VIDEO_URL))
        val scope = CoroutineScope(SupervisorJob())

        try {
            repair.schedulePlaylists(scope, TEST_USER_ID)
            repair.schedulePlaylists(scope, TEST_USER_ID)

            withTimeout(5_000) { repairStarted.await() }
            assertEquals(1, attempts.get())
            releaseRepair.complete(Unit)
            withTimeout(5_000) {
                while (scope.coroutineContext.job.children.any()) delay(10)
            }

            repair.schedulePlaylists(scope, TEST_USER_ID)
            delay(100)

            assertEquals(2, attempts.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `repair continues across more than one metadata batch`() = runBlocking {
        val attempts = AtomicInteger()
        val repair = UserVideoMetadataRepairService(VideoMetadataResolver(alwaysSuccessfulStreamService(attempts)))
        val playlist = playlists.create(TEST_USER_ID, PlaylistItem(name = "Imported", description = ""))
        repeat(26) { index ->
            val url = "https://www.youtube.com/watch?v=video$index"
            playlists.addVideo(TEST_USER_ID, playlist.id, fallbackVideo(url))
        }
        val scope = CoroutineScope(SupervisorJob())

        try {
            repair.schedulePlaylists(scope, TEST_USER_ID)
            withTimeout(10_000) {
                while (playlists.getById(TEST_USER_ID, playlist.id)?.videos?.count { it.title.startsWith("Resolved") } != 26) {
                    delay(20)
                }
            }
            assertEquals(26, attempts.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `transient metadata failure is retried once`() = runBlocking {
        val attempts = AtomicInteger()
        val streamService = object : StreamService {
            override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
                return if (attempts.incrementAndGet() == 1) {
                    ExtractionResult.Failure("Provider blocked", "provider_access_blocked")
                } else {
                    ExtractionResult.Success(stream(url))
                }
            }
        }
        val repair = UserVideoMetadataRepairService(VideoMetadataResolver(streamService))
        val playlist = playlists.create(TEST_USER_ID, PlaylistItem(name = "Imported", description = ""))
        playlists.addVideo(TEST_USER_ID, playlist.id, fallbackVideo(VIDEO_URL))
        val scope = CoroutineScope(SupervisorJob())

        try {
            repair.schedulePlaylists(scope, TEST_USER_ID)
            withTimeout(10_000) {
                while (playlists.getById(TEST_USER_ID, playlist.id)?.videos?.single()?.title != "Resolved abc123") {
                    delay(20)
                }
            }
            assertEquals(2, attempts.get())
        } finally {
            scope.cancel()
        }
    }

    private fun fallbackVideo(url: String): PlaylistVideoItem = PlaylistVideoItem(
        url = url,
        title = "YouTube video ${url.substringAfterLast('=')}",
        thumbnail = "https://i.ytimg.com/vi/${url.substringAfterLast('=')}/hqdefault.jpg",
        duration = 0L,
    )

    private fun alwaysSuccessfulStreamService(attempts: AtomicInteger): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            attempts.incrementAndGet()
            return ExtractionResult.Success(stream(url))
        }
    }

    private fun fakeStreamService(started: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>): StreamService = object : StreamService {
        override suspend fun getStreamInfo(url: String): ExtractionResult<StreamResponse> {
            started.complete(Unit)
            release.await()
            return ExtractionResult.Success(stream(url))
        }
    }

    private fun stream(url: String): StreamResponse = StreamResponse(
        id = "abc123", title = "Resolved abc123", uploaderName = "Channel", uploaderUrl = "https://www.youtube.com/channel/UC1", uploaderAvatarUrl = "https://avatar.test/uc1.jpg",
        thumbnailUrl = "https://thumb.test/abc123.jpg", description = "", duration = 120L, viewCount = 10L, likeCount = 0L, dislikeCount = 0L, uploadDate = "", uploaded = 1L,
        uploaderSubscriberCount = 0L, uploaderVerified = false, category = "", license = "", visibility = "", tags = emptyList(), streamType = "video", isShortFormContent = false,
        requiresMembership = false, startPosition = 0L, streamSegments = emptyList(), hlsUrl = "", dashMpdUrl = "", videoStreams = emptyList(), audioStreams = emptyList(),
        originalAudioTrackId = null, preferredDefaultAudioTrackId = null, videoOnlyStreams = emptyList(), subtitles = emptyList(), previewFrames = emptyList(), sponsorBlockSegments = emptyList(), relatedStreams = emptyList(),
    )
}
