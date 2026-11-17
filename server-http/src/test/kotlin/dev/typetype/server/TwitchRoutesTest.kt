package dev.typetype.server

import dev.typetype.server.routes.twitchRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.TwitchAccount
import dev.typetype.server.services.TwitchAccountStore
import dev.typetype.server.services.TwitchConfig
import dev.typetype.server.services.TwitchLiveClient
import dev.typetype.server.services.TwitchService
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchRoutesTest {
    private val config = TwitchConfig("http://live.test", "s".repeat(32), "k".repeat(32))
    private val requests = mutableListOf<Request>()
    private val store = mockk<TwitchAccountStore>(relaxed = true)
    private var answer: (Request) -> Pair<Int, String> = { 200 to """{"items":[],"hasMore":false}""" }

    private val okClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        requests += request
        val (code, body) = answer(request)
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()

    private fun withApp(enabled: Boolean = true, block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val cfg = if (enabled) config else config.copy(serviceUrl = null)
        val service = TwitchService(cfg, TwitchLiveClient(cfg, okClient), store)
        application {
            install(ContentNegotiation) { json() }
            routing { twitchRoutes(service, AuthService.fixed(TEST_USER_ID)) }
        }
        block()
    }

    @Test
    fun `routes answer 404 when twitch is disabled`() = withApp(enabled = false) {
        val response = client.get("/twitch/streams")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("twitch_disabled"))
    }

    @Test
    fun `browse relays status body secret and paging`() = withApp {
        answer = { 200 to """{"items":[{"id":"1"}],"hasMore":true,"cursor":"c"}""" }
        val response = client.get("/twitch/streams?first=10&after=abc&evil=1")
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"cursor\":\"c\""))
        val sent = requests.single()
        assertEquals("/v1/streams", sent.url.encodedPath)
        assertEquals("10", sent.url.queryParameter("first"))
        assertEquals(null, sent.url.queryParameter("evil"))
        assertEquals("s".repeat(32), sent.header("X-Live-Secret"))
    }

    @Test
    fun `tag lives are relayed for any script and refused for anything but one word`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/twitch/tags/Speedrun/streams?first=12").status)
        assertEquals(HttpStatusCode.OK, client.get("/twitch/tags/%E6%97%A5%E6%9C%AC%E8%AA%9E/streams").status)
        assertEquals(listOf("/v1/tags/Speedrun/streams", "/v1/tags/%E6%97%A5%E6%9C%AC%E8%AA%9E/streams"), requests.map { it.url.encodedPath })
        assertEquals("12", requests.first().url.queryParameter("first"))
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/tags/two%20words/streams").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/tags/a.b/streams").status)
        assertEquals(2, requests.size)
    }

    @Test
    fun `the chat of a past broadcast is relayed with its offset or cursor`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/twitch/videos/2929630714/comments?offset=120&evil=1").status)
        assertEquals(HttpStatusCode.OK, client.get("/twitch/videos/2929630714/comments?cursor=abc").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/videos/abc/comments").status)
        assertEquals("/v1/videos/2929630714/comments", requests.first().url.encodedPath)
        assertEquals("120", requests.first().url.queryParameter("offset"))
        assertEquals(null, requests.first().url.queryParameter("evil"))
        assertEquals("abc", requests.last().url.queryParameter("cursor"))
        assertEquals(2, requests.size)
    }

    @Test
    fun `the poll and pinned message of a chat are relayed`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/twitch/chat/somechannel/highlights?evil=1").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/chat/bad%20login/highlights").status)
        assertEquals("/v1/chat/somechannel/highlights", requests.single().url.encodedPath)
        assertEquals(null, requests.single().url.queryParameter("evil"))
    }

    @Test
    fun `the chapters of a past broadcast are relayed`() = withApp {
        assertEquals(HttpStatusCode.OK, client.get("/twitch/videos/2929630714/chapters?evil=1").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/videos/abc/chapters").status)
        assertEquals("/v1/videos/2929630714/chapters", requests.single().url.encodedPath)
        assertEquals(null, requests.single().url.queryParameter("evil"))
    }

    @Test
    fun `errors from the live service keep their status`() = withApp {
        answer = { 404 to """{"error":"offline"}""" }
        val response = client.get("/twitch/live/somechannel")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(response.bodyAsText().contains("offline"))
    }

    @Test
    fun `invalid path parameters are refused before the live service`() = withApp {
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/channels/bad%20login").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/twitch/videos/abc").status)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `live sends the linked twitch token`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        client.get("/twitch/live/somechannel") { bearerAuth("test-jwt") }
        assertEquals("user-token", requests.single().header("X-Twitch-Token"))
    }

    @Test
    fun `account routes need a signed in user`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/twitch/account").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("/twitch/account/device").status)
    }

    @Test
    fun `poll stores the encrypted account and never returns tokens`() = withApp {
        answer = {
            200 to """{"status":"ok","token":{"accessToken":"a","refreshToken":"r","expiresIn":100},
                "user":{"login":"me","displayName":"Me","avatarUrl":"https://static-cdn.jtvnw.net/x.png"}}"""
        }
        val saved = slot<TwitchAccount>()
        coEvery { store.save(TEST_USER_ID, capture(saved)) } returns Unit
        val response = client.post("/twitch/account/poll") {
            bearerAuth("test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"deviceCode":"dev"}""")
        }
        val body = response.bodyAsText()
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(body.contains("\"linked\":true") && !body.contains("\"a\"") && !body.contains("refresh"))
        assertEquals("a", saved.captured.accessToken)
        assertEquals("me", saved.captured.login)
    }

    @Test
    fun `pending poll stores nothing`() = withApp {
        answer = { 200 to """{"status":"pending"}""" }
        val response = client.post("/twitch/account/poll") {
            bearerAuth("test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"deviceCode":"dev"}""")
        }
        assertTrue(response.bodyAsText().contains("pending"))
        coVerify(exactly = 0) { store.save(any(), any()) }
    }

    @Test
    fun `followed lives refresh an expired token once`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        answer = { request ->
            when {
                request.url.encodedPath == "/v1/auth/refresh" -> 200 to """{"accessToken":"new","refreshToken":"r2","expiresIn":100}"""
                request.header("X-Twitch-Token") == "user-token" -> 401 to """{"error":"twitch_token_rejected"}"""
                else -> 200 to """{"items":[],"hasMore":false}"""
            }
        }
        val response = client.get("/twitch/me/live") { bearerAuth("test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("new", requests.last().header("X-Twitch-Token"))
        coVerify { store.save(TEST_USER_ID, match { it.accessToken == "new" && it.refreshToken == "r2" }) }
    }

    @Test
    fun `unlink revokes and deletes`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        assertEquals(HttpStatusCode.NoContent, client.delete("/twitch/account") { bearerAuth("test-jwt") }.status)
        assertEquals("/v1/auth/revoke", requests.single().url.encodedPath)
        coVerify { store.delete(TEST_USER_ID) }
    }

    @Test
    fun `follow and unfollow relay the method and the linked token`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        answer = { 200 to """{"channelId":"42","following":true}""" }
        val response = client.put("/twitch/me/follows/somechannel") { bearerAuth("test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        client.delete("/twitch/me/follows/somechannel") { bearerAuth("test-jwt") }
        assertEquals(listOf("PUT", "DELETE"), requests.map { it.method })
        assertTrue(requests.all { it.url.encodedPath == "/v1/me/follows/somechannel" && it.header("X-Twitch-Token") == "user-token" })
    }

    @Test
    fun `the emotes of the linked account are relayed with its token`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        answer = { 200 to """{"channelId":"42","emotes":[{"id":"25","name":"Kappa","type":"GLOBALS"}]}""" }
        val response = client.get("/twitch/me/emotes/somechannel") { bearerAuth("test-jwt") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("Kappa"))
        assertTrue(requests.all { it.url.encodedPath == "/v1/me/emotes/somechannel" && it.header("X-Twitch-Token") == "user-token" })
    }

    @Test
    fun `follow without a linked account is a conflict`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns null
        val response = client.put("/twitch/me/follows/somechannel") { bearerAuth("test-jwt") }
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a poll vote is relayed with the linked token`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        answer = { 204 to "" }
        val response = client.post("/twitch/polls/vote") {
            bearerAuth("test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"pollId":"p-1","choiceId":"c-2"}""")
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
        val sent = requests.single()
        assertEquals("/v1/polls/vote", sent.url.encodedPath)
        assertEquals("user-token", sent.header("X-Twitch-Token"))
    }

    @Test
    fun `a malformed poll vote never reaches the live service`() = withApp {
        coEvery { store.get(TEST_USER_ID) } returns account()
        val response = client.post("/twitch/polls/vote") {
            bearerAuth("test-jwt")
            contentType(ContentType.Application.Json)
            setBody("""{"pollId":"p 1","choiceId":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(requests.isEmpty())
    }

    private fun account() = TwitchAccount("user-token", "refresh", "me", "Me", "", 0)
}
