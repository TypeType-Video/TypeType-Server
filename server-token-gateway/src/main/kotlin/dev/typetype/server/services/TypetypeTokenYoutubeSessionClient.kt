package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import dev.typetype.server.sabr.YoutubeSabrInfo
import java.io.IOException

class TypetypeTokenYoutubeSessionClient(
    private val tokenServiceUrl: String,
    client: OkHttpClient = OkHttpClient(),
) {
    internal val client: OkHttpClient = TypetypeTokenHttpClient.bounded(client)
    private val sessionFetcher = TokenYoutubeSabrSessionFetcher(tokenServiceUrl, this.client)

    suspend fun importSessionBinding(cookies: String): TokenSessionBinding? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("cookies", cookies).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${tokenServiceUrl.trimEnd('/')}/session-binding")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        null
                    } else {
                        val payload = JSONObject(response.body?.string().orEmpty())
                        TokenSessionBinding(
                            cookies = payload.optString("cookies"),
                            poToken = payload.optString("poToken"),
                            authUser = payload.optInt("authUser"),
                        ).takeIf { it.cookies.isNotBlank() && it.poToken.isNotBlank() }
                    }
                }
            } catch (_: IOException) {
                null
            }
        }

    suspend fun startDeviceLogin(accountKey: String): TokenDeviceStart? =
        deviceCall("/device/start", accountKey, "accountKey")?.let { payload ->
            TokenDeviceStart(
                userCode = payload.optString("userCode"),
                verificationUrl = payload.optString("verificationUrl"),
                expiresAt = payload.optLong("expiresAt"),
                intervalSeconds = payload.optInt("intervalSeconds"),
            ).takeIf { it.userCode.isNotBlank() && it.verificationUrl.isNotBlank() }
        }

    suspend fun pollDeviceLogin(accountKey: String): TokenDevicePoll? =
        deviceCall("/device/poll", accountKey, "accountKey")?.let { payload ->
            TokenDevicePoll(
                status = payload.optString("status"),
                tokens = payload.optJSONObject("tokens")?.let { tokens ->
                    TokenDeviceTokens(
                        accessToken = tokens.optString("accessToken"),
                        refreshToken = tokens.optString("refreshToken").ifBlank { null },
                        expiresIn = tokens.optLong("expiresIn"),
                    )
                },
            )
        }

    suspend fun refreshDeviceToken(refreshToken: String): TokenDeviceTokens? =
        deviceCall("/device/refresh", refreshToken, "refreshToken")?.let { payload ->
            TokenDeviceTokens(
                accessToken = payload.optString("accessToken"),
                refreshToken = payload.optString("refreshToken").ifBlank { refreshToken },
                expiresIn = payload.optLong("expiresIn"),
            ).takeIf { it.accessToken.isNotBlank() }
        }

    suspend fun revokeDeviceToken(token: String) {
        deviceCall("/device/revoke", token, "token")
    }

    suspend fun fetchTvPoToken(refresh: Boolean = false): TokenTvPoToken? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${tokenServiceUrl.trimEnd('/')}/tv-token" + if (refresh) "?refresh=true" else "")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val payload = JSONObject(response.body?.string().orEmpty())
                TokenTvPoToken(
                    poToken = payload.optString("poToken"),
                    visitorData = payload.optString("visitorData"),
                    livingRoomPoTokenId = payload.optString("livingRoomPoTokenId"),
                    generation = TokenFreshness.generation(payload),
                    expiresAtMs = TokenFreshness.expiresAtMs(payload),
                ).takeIf {
                    it.poToken.isNotBlank() &&
                        it.visitorData.isNotBlank() &&
                        it.livingRoomPoTokenId.isNotBlank()
                }
            }
        } catch (_: IOException) {
            null
        }
    }

    suspend fun cancelDeviceLogin(accountKey: String) {
        deviceCall("/device/cancel", accountKey, "accountKey")
    }

    private suspend fun deviceCall(path: String, value: String, field: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put(field, value).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("${tokenServiceUrl.trimEnd('/')}$path")
                .post(body)
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val text = response.body?.string().orEmpty()
                    if (text.isBlank()) JSONObject() else JSONObject(text)
                }
            } catch (_: IOException) {
                null
            }
        }

    suspend fun fetchHlsManifestUrl(videoId: String): String? = fetchSession(videoId)
        ?.optString("hlsManifestUrl")
        ?.trim()
        ?.ifBlank { null }

    suspend fun fetchSabrInfo(videoId: String): YoutubeSabrInfo? = fetchSession(videoId)?.toSabrInfo(videoId)

    suspend fun fetchPlaybackSession(
        videoId: String,
        isolated: Boolean = false,
    ): TokenYoutubeSession? = fetchPlaybackSessionResult(videoId, isolated).valueOrNull()

    suspend fun fetchPlaybackSessionResult(
        videoId: String,
        isolated: Boolean = false,
    ): TokenServiceResult<TokenYoutubeSession> = when (val result = sessionFetcher.fetch(videoId, isolated)) {
        is TokenServiceResult.Success -> result.value.toPlaybackSession(videoId)
            ?.let { TokenServiceResult.Success(it) }
            ?: TokenServiceResult.Failure(TokenServiceFailureReason.INVALID_RESPONSE, 200)
        is TokenServiceResult.Failure -> result
    }

    private suspend fun fetchSession(videoId: String): JSONObject? = sessionFetcher.fetch(videoId, isolated = false).valueOrNull()
}
