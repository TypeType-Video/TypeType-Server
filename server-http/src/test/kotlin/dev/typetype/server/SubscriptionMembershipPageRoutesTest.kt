package dev.typetype.server

import dev.typetype.server.models.SubscriptionItem
import dev.typetype.server.models.SubscriptionMembershipPage
import dev.typetype.server.routes.subscriptionsRoutes
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SubscriptionGroupsService
import dev.typetype.server.services.SubscriptionGroupWriteResult
import dev.typetype.server.services.SubscriptionsService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SubscriptionMembershipPageRoutesTest {
    private val subscriptions = SubscriptionsService()
    private val groups = SubscriptionGroupsService()
    private val path = "/subscriptions/group-memberships"

    companion object {
        @BeforeAll
        @JvmStatic
        fun initDb(): Unit = TestDatabase.setup()
    }

    @BeforeEach
    fun clean(): Unit = TestDatabase.truncateAll()

    private fun withApp(block: suspend ApplicationTestBuilder.() -> Unit): Unit = testApplication {
        application {
            install(ContentNegotiation) { json() }
            routing { subscriptionsRoutes(subscriptions, AuthService.fixed(TEST_USER_ID), groupsService = groups) }
        }
        block()
    }

    @Test
    fun `page and lookup require authentication`() = withApp {
        assertEquals(HttpStatusCode.Unauthorized, client.get("$path/page").status)
        assertEquals(HttpStatusCode.Unauthorized, client.post("$path/lookup").status)
    }

    @Test
    fun `page validates limits filters and group ownership`() = withApp {
        for (query in listOf("page=-1", "page=x", "page=1000001", "limit=0", "limit=101", "limit=x",
            "groupId=", "excluded=true", "ungrouped=maybe", "excluded=maybe", "groupId=x&ungrouped=true",
            "search=${"a".repeat(201)}")) {
            assertEquals(HttpStatusCode.BadRequest, get("$path/page?$query").status, query)
        }
        val foreign = (groups.create("foreign", "Hidden") as SubscriptionGroupWriteResult.Success).group
        for (id in listOf("missing", foreign.id)) {
            val response = get("$path/page?groupId=$id")
            assertEquals(HttpStatusCode.NotFound, response.status)
            assertTrue(response.bodyAsText().contains("subscription_group_not_found"))
        }
    }

    @Test
    fun `page contract returns requested rows and accurate counts without changing legacy reads`() = withApp {
        for (index in 0..2) subscriptions.add(TEST_USER_ID, SubscriptionItem("https://example.com/channel/$index", "Name $index", "avatar"))
        val response = get("$path/page?page=1&limit=2&search=NAME")
        assertEquals(HttpStatusCode.OK, response.status)
        val page = Json.decodeFromString<SubscriptionMembershipPage>(response.bodyAsText())
        assertEquals(1, page.page)
        assertEquals(2, page.limit)
        assertEquals(3L, page.total)
        assertEquals(3L, page.totalSubscriptions)
        assertEquals(3L, page.ungroupedCount)
        assertEquals("Name 2", page.items.single().name)
        assertTrue(get(path).bodyAsText().startsWith("["))
    }

    @Test
    fun `lookup enforces bounded bodies and returns only current owned subscriptions`() = withApp {
        subscriptions.add(TEST_USER_ID, SubscriptionItem("https://example.com/channel/one", "One", "avatar"))
        val valid = lookup("""{"channelUrls":["https://example.com/channel/one","missing"]}""")
        assertEquals(HttpStatusCode.OK, valid.status)
        assertTrue(valid.bodyAsText().contains("\"name\":\"One\""))
        assertEquals(HttpStatusCode.BadRequest, lookup("""{"channelUrls":[]}""").status)
        assertEquals(HttpStatusCode.BadRequest, lookup("""{"channelUrls":["${"x".repeat(2049)}"]}""").status)
        assertEquals(HttpStatusCode.BadRequest, lookup("{\"channelUrls\":[${List(501) { "\"x\"" }.joinToString(",")}]}").status)
        assertEquals(HttpStatusCode.PayloadTooLarge, lookup("x".repeat(1024 * 1024 + 1)).status)
    }

    private suspend fun ApplicationTestBuilder.get(url: String): io.ktor.client.statement.HttpResponse = client.get(url) {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
    }

    private suspend fun ApplicationTestBuilder.lookup(body: String): io.ktor.client.statement.HttpResponse = client.post("$path/lookup") {
        header(HttpHeaders.Authorization, "Bearer test-jwt")
        header(HttpHeaders.ContentType, "application/json")
        setBody(body)
    }
}
