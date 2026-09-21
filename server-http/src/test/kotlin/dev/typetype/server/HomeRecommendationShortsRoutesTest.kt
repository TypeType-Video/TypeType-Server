package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.homeRecommendationShortsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationShortsRoutesTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val resolverDeps = homeResolverDependencies(
        subscriptions = SubscriptionsService(),
        channelService = channelService,
        cache = cache,
        trendingService = trendingService,
    )
    private val service = HomeRecommendationService(
        poolResolver = buildHomeResolver(resolverDeps),
    )
    private val auth = AuthService.fixed(TEST_USER_ID)

    companion object { @BeforeAll @JvmStatic fun initDb() = TestDatabase.setup() }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        coEvery { cache.get(any()) } returns null
        coEvery { cache.set(any(), any(), any()) } returns Unit
        coEvery { searchService.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(SearchPageResponse(emptyList(), null, null, false))
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(
            listOf(video("s1", 40, true), video("l1", 400, false), video("s2", 55, true)),
        )
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationShortsRoutes(service, auth, resolverDeps.blockedService) }
        }
        block()
    }

    @Test
    fun `shorts endpoint requires auth`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/recommendations/shorts").status)
    }

    @Test
    fun `shorts endpoint returns short items only`() = withApp {
        val response = client.get("/recommendations/shorts?limit=5&intent=quick") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.UserAgent, "Android Mobile")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(!body.contains("/v/l1"))
    }

    private fun video(id: String, duration: Long, short: Boolean): VideoItem = VideoItem(
        id = id,
        title = id,
        url = "https://yt.com/v/$id",
        thumbnailUrl = "",
        uploaderName = "Channel",
        uploaderUrl = "https://yt.com/c/$id",
        uploaderAvatarUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        uploaded = System.currentTimeMillis(),
        streamType = "video_stream",
        isShortFormContent = short,
        uploaderVerified = false,
        shortDescription = null,
    )
}
