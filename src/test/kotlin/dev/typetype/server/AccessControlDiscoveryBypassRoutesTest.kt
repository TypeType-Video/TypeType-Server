package dev.typetype.server

import dev.typetype.server.db.tables.UsersTable
import dev.typetype.server.models.AdminSettingsItem
import dev.typetype.server.models.ChannelResultItem
import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.models.SearchPageResponse
import dev.typetype.server.models.SettingsItem
import dev.typetype.server.routes.searchRoutes
import dev.typetype.server.services.AccessControlService
import dev.typetype.server.services.AdminSettingsService
import dev.typetype.server.services.AllowedChannelsService
import dev.typetype.server.services.AllowedPlaylistsService
import dev.typetype.server.services.AuthService
import dev.typetype.server.services.SearchService
import dev.typetype.server.services.SettingsService
import dev.typetype.server.services.UserAdminService
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val UNRESTRICTED_USER_ID = "unrestricted-user"
private const val ALLOW_LIST_USER_ID = "allow-list-user"

class AccessControlDiscoveryBypassRoutesTest {
    private val settings = SettingsService()
    private val channels = AllowedChannelsService()
    private val playlists = AllowedPlaylistsService()
    private val adminSettings = AdminSettingsService()
    private val access = AccessControlService(settings, channels, playlists, adminSettings)
    private val users = UserAdminService()
    private val search: SearchService = mockk()

    companion object { @BeforeAll @JvmStatic fun initDb() { TestDatabase.setup() } }

    @BeforeEach
    fun clean() {
        TestDatabase.truncateAll()
        insertUser(TEST_USER_ID, "admin")
        insertUser(UNRESTRICTED_USER_ID, "user")
        insertUser(ALLOW_LIST_USER_ID, "user")
    }

    @Test
    fun `admin bypasses global allow list discovery filtering`() = withApp(AuthService.fixed(TEST_USER_ID)) {
        enableGlobalAllowList()
        assertTrue(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `admin is filtered by global allow list when applyParentalControlsToAdmins is enabled`() = withApp(AuthService.fixed(TEST_USER_ID)) {
        enableGlobalAllowList(applyToAdmins = true)
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
        channels.addChannel(TEST_USER_ID, "https://youtube.com/@electroboom", "ElectroBOOM", global = true)
        assertTrue(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `admin with explicit unrestricted override bypasses global allow list even when applyParentalControlsToAdmins is enabled`() = withApp(AuthService.fixed(TEST_USER_ID)) {
        enableGlobalAllowList(applyToAdmins = true)
        users.setAccessMode(TEST_USER_ID, "unrestricted")
        assertTrue(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `admin with user allow list is filtered when global is unrestricted and applyParentalControlsToAdmins is enabled`() = withApp(AuthService.fixed(TEST_USER_ID)) {
        adminSettings.upsert(AdminSettingsItem(accessMode = "unrestricted", applyParentalControlsToAdmins = true))
        settings.upsert(TEST_USER_ID, SettingsItem(accessMode = "allow_list"))
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `normal unrestricted user remains filtered by global allow list`() = withApp(AuthService.fixed(UNRESTRICTED_USER_ID)) {
        enableGlobalAllowList()
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
        settings.upsert(UNRESTRICTED_USER_ID, SettingsItem(accessMode = "unrestricted"))
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `admin managed unrestricted user bypasses global allow list discovery filtering`() = withApp(AuthService.fixed(UNRESTRICTED_USER_ID)) {
        enableGlobalAllowList()
        users.setAccessMode(UNRESTRICTED_USER_ID, "unrestricted")
        assertTrue(searchBodyWithAuth().contains("ElectroBOOM"))
        settings.upsert(UNRESTRICTED_USER_ID, SettingsItem(accessMode = "unrestricted"))
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `allow list user remains filtered by global allow list`() = withApp(AuthService.fixed(ALLOW_LIST_USER_ID)) {
        enableGlobalAllowList()
        settings.upsert(ALLOW_LIST_USER_ID, SettingsItem(accessMode = "allow_list"))
        assertFalse(searchBodyWithAuth().contains("ElectroBOOM"))
    }

    @Test
    fun `anonymous remains filtered by global allow list`() = withApp(AuthService.fixed(TEST_USER_ID)) {
        enableGlobalAllowList()
        assertFalse(client.get("/search?q=ElectroBOOM&service=0").bodyAsText().contains("ElectroBOOM"))
    }

    private fun withApp(auth: AuthService, block: suspend io.ktor.server.testing.ApplicationTestBuilder.() -> Unit) = testApplication {
        coEvery { search.search(any(), any(), any(), any(), any()) } returns ExtractionResult.Success(searchResponse())
        application { install(ContentNegotiation) { json() }; routing { searchRoutes(search, auth, access) } }
        block()
    }

    private suspend fun enableGlobalAllowList(applyToAdmins: Boolean = false) {
        adminSettings.upsert(AdminSettingsItem(accessMode = "allow_list", applyParentalControlsToAdmins = applyToAdmins))
        channels.addChannel(TEST_USER_ID, "https://youtube.com/@alreadyallowed", "Already Allowed", global = true)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.searchBodyWithAuth(): String =
        client.get("/search?q=ElectroBOOM&service=0") { headers.append(HttpHeaders.Authorization, "Bearer test-jwt") }.bodyAsText()

    private fun searchResponse() = SearchPageResponse(
        items = emptyList(),
        nextpage = null,
        searchSuggestion = null,
        isCorrectedSearch = false,
        channels = listOf(ChannelResultItem("id", "ElectroBOOM", "https://youtube.com/@electroboom", "", "", 0, 0, false)),
    )
}

private fun insertUser(userId: String, role: String) = transaction {
    UsersTable.insert { it[id] = userId; it[email] = "$userId@test.local"; it[passwordHash] = "hash"; it[name] = userId; it[UsersTable.role] = role; it[createdAt] = 10L; it[updatedAt] = 10L }
}
