package dev.typetype.server.services

import dev.typetype.server.models.TwitchAccountStatus
import dev.typetype.server.models.TwitchPollResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.Response

class TwitchService(
    private val config: TwitchConfig,
    private val client: TwitchLiveClient = TwitchLiveClient(config),
    private val store: TwitchAccountStore? = config.tokenKey?.takeIf { config.enabled }?.let { TwitchAccountStore(TwitchTokenCrypto(it)) },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    val enabled: Boolean get() = config.enabled && store != null

    suspend fun relay(path: String, query: Map<String, String>, userId: String?): TwitchLiveResponse {
        val token = userId?.let { freshAccount(it)?.accessToken }
        return client.get(path, query, token)
    }

    suspend fun startDevice(): TwitchLiveResponse = client.post("/v1/auth/device", "{}")

    suspend fun poll(userId: String, deviceCode: String): Pair<Int, TwitchPollResponse?> {
        val body = buildJsonObject { put("deviceCode", deviceCode) }.toString()
        val response = client.post("/v1/auth/poll", body)
        if (response.status != HTTP_OK) return response.status to null
        val json = parse(response.body) ?: return HTTP_BAD_GATEWAY to null
        val status = json.text("status") ?: return HTTP_BAD_GATEWAY to null
        if (status != "ok") return HTTP_OK to TwitchPollResponse(status = status)
        val token = json["token"]?.jsonObject ?: return HTTP_BAD_GATEWAY to null
        val user = json["user"]?.jsonObject ?: return HTTP_BAD_GATEWAY to null
        val account = TwitchAccount(
            accessToken = token.text("accessToken") ?: return HTTP_BAD_GATEWAY to null,
            refreshToken = token.text("refreshToken"),
            login = user.text("login").orEmpty(),
            displayName = user.text("displayName").orEmpty(),
            avatarUrl = user.text("avatarUrl").orEmpty(),
            expiresAt = expiry(token),
        )
        requireNotNull(store).save(userId, account)
        return HTTP_OK to TwitchPollResponse(status = "ok", account = account.status())
    }

    suspend fun status(userId: String): TwitchAccountStatus =
        store?.get(userId)?.status() ?: TwitchAccountStatus(linked = false)

    suspend fun unlink(userId: String) {
        val account = store?.get(userId) ?: return
        client.post("/v1/auth/revoke", buildJsonObject { put("token", account.accessToken) }.toString())
        store.delete(userId)
    }

    suspend fun withAccount(userId: String, block: suspend (String) -> TwitchLiveResponse): TwitchLiveResponse? {
        val account = freshAccount(userId) ?: return null
        val response = block(account.accessToken)
        if (response.status != HTTP_UNAUTHORIZED) return response
        val refreshed = refresh(userId, account) ?: return response
        return block(refreshed.accessToken)
    }

    suspend fun authed(userId: String, path: String, query: Map<String, String>): TwitchLiveResponse? =
        withAccount(userId) { token -> client.get(path, query, token) }

    suspend fun sendChat(userId: String, login: String, json: String): TwitchLiveResponse? =
        withAccount(userId) { token -> client.post("/v1/chat/$login/messages", json, token) }

    suspend fun votePoll(userId: String, json: String): TwitchLiveResponse? =
        withAccount(userId) { token -> client.post("/v1/polls/vote", json, token) }

    suspend fun setFollow(userId: String, login: String, follow: Boolean): TwitchLiveResponse? =
        withAccount(userId) { token ->
            if (follow) client.put("/v1/me/follows/$login", token) else client.delete("/v1/me/follows/$login", token)
        }

    fun openChat(login: String): Response = client.openStream("/v1/chat/$login/events")

    private suspend fun freshAccount(userId: String): TwitchAccount? {
        val account = store?.get(userId) ?: return null
        if (account.expiresAt == 0L || account.expiresAt > nowMillis() + REFRESH_MARGIN_MS) return account
        return refresh(userId, account) ?: account
    }

    private suspend fun refresh(userId: String, account: TwitchAccount): TwitchAccount? {
        val refreshToken = account.refreshToken ?: return null
        val response = client.post("/v1/auth/refresh", buildJsonObject { put("refreshToken", refreshToken) }.toString())
        if (response.status != HTTP_OK) return null
        val token = parse(response.body) ?: return null
        val updated = account.copy(
            accessToken = token.text("accessToken") ?: return null,
            refreshToken = token.text("refreshToken") ?: refreshToken,
            expiresAt = expiry(token),
        )
        store?.save(userId, updated)
        return updated
    }

    private fun expiry(token: JsonObject): Long =
        token["expiresIn"]?.jsonPrimitive?.long?.takeIf { it > 0 }?.let { nowMillis() + it * MILLIS } ?: 0L

    private fun TwitchAccount.status() =
        TwitchAccountStatus(linked = true, login = login, displayName = displayName, avatarUrl = avatarUrl)

    private fun parse(body: String): JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()

    private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val HTTP_OK = 200
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_BAD_GATEWAY = 502
        const val REFRESH_MARGIN_MS = 60_000L
        const val MILLIS = 1_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
