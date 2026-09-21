package dev.typetype.server

import dev.typetype.server.cache.CacheService
import dev.typetype.server.models.ChannelResponse
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.VideoItem
import dev.typetype.server.routes.homeRecommendationRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.ChannelService
import dev.typetype.server.services.HomeRecommendationService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SubscriptionsService
import dev.typetype.server.services.TrendingService
import io.ktor.client.request.get
import io.ktor.client.request.headers
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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeRecommendationRoutesTest {
    private val cache: CacheService = mockk()
    private val channelService: ChannelService = mockk()
    private val trendingService: TrendingService = mockk()
    private val searchService: SearchService = mockk()
    private val subscriptions = SubscriptionsService()
    private val resolverDeps = homeResolverDependencies(
        subscriptions = subscriptions,
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
    }

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { homeRecommendationRoutes(service, auth, resolverDeps.blockedService) }
        }
        block()
    }

    @Test
    fun `GET recommendations home without token returns 401`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/recommendations/home").status)
    }

    @Test
    fun `GET recommendations home returns items and cursor`() = withApp {
        val now = System.currentTimeMillis()
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://yt.com/c/a", "A", ""))
        coEvery { channelService.getChannel("https://yt.com/c/a", null) } returns ExtractionResult.Success(
            ChannelResponse("A", "", "", "", 0L, false, listOf(video("v1", now), video("v2", now - 1)), null),
        )
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(listOf(video("t1", now - 2)))
        val response = client.get("/recommendations/home?limit=2") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET recommendations home accepts intent parameter`() = withApp {
        coEvery { trendingService.getTrending(any()) } returns ExtractionResult.Success(listOf(video("t2", System.currentTimeMillis())))
        val response = client.get("/recommendations/home?limit=2&intent=quick") {
            headers.append(HttpHeaders.Authorization, "Bearer test-jwt")
            headers.append(HttpHeaders.UserAgent, "Android Mobile")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun video(id: String, uploaded: Long): VideoItem = VideoItem(
        id, id, "https://yt.com/v/$id", "", "Channel", "https://yt.com/c/$id", "", 60, 0, "", uploaded,
        "video_stream", false, false, null,
    )
}
