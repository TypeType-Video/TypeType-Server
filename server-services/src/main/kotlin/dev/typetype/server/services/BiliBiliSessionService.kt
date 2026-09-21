package dev.typetype.server.services

import dev.typetype.server.models.BiliBiliQrLoginResponse
import dev.typetype.server.models.BiliBiliQrPollResponse
import dev.typetype.server.models.BiliBiliSessionStatusResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class BiliBiliSessionService(
    private val crypto: BiliBiliSessionCrypto?,
    private val store: BiliBiliSessionStore = BiliBiliSessionStore(),
    private val qrLoginService: BiliBiliQrLoginService = BiliBiliQrLoginService(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    val isConfigured: Boolean = crypto != null

    suspend fun startQrLogin(): BiliBiliQrLoginResult {
        if (!isConfigured) return BiliBiliQrLoginResult.Unavailable
        return when (val result = qrLoginService.generate()) {
            is BiliBiliQrGenerateResult.Success ->
                BiliBiliQrLoginResult.Success(
                    BiliBiliQrLoginResponse(
                        qrUrl = result.qrUrl,
                        qrcodeKey = result.qrcodeKey,
                        expiresAt = result.expiresAt,
                    ),
                )
            is BiliBiliQrGenerateResult.Error -> BiliBiliQrLoginResult.Error(result.message)
        }
    }

    suspend fun pollQrLogin(userId: String, qrcodeKey: String): BiliBiliQrPollResponse {
        if (!isConfigured) return BiliBiliQrPollResponse("unavailable", "BiliBili session is unavailable")
        val crypto = crypto ?: return BiliBiliQrPollResponse("unavailable", "BiliBili session is unavailable")
        return when (val result = qrLoginService.poll(qrcodeKey)) {
            is BiliBiliQrPollResult.Confirmed -> {
                val cookies = BilibiliCookieConfig.fromRaw(result.cookieHeader)
                if (!cookies.isConfigured) {
                    return BiliBiliQrPollResponse("error", "Invalid BiliBili cookies received")
                }
                store.completeForUser(
                    userId = userId,
                    encryptedCookies = crypto.encrypt(result.cookieHeader),
                    expiresAt = parseSessDataExpiry(result.cookieHeader) ?: estimateExpiry(),
                )
                BiliBiliQrPollResponse("confirmed")
            }
            is BiliBiliQrPollResult.Scanned -> BiliBiliQrPollResponse("scanned")
            is BiliBiliQrPollResult.Waiting -> BiliBiliQrPollResponse("waiting")
            is BiliBiliQrPollResult.Expired -> BiliBiliQrPollResponse("expired")
            is BiliBiliQrPollResult.Error -> BiliBiliQrPollResponse("error", result.message)
        }
    }

    suspend fun status(userId: String): BiliBiliSessionStatusResponse {
        if (!isConfigured) return BiliBiliSessionStatusResponse(BiliBiliSessionStatus.Disconnected.value, 0, 0)
        val stored = store.status(userId)
        if (stored.status == BiliBiliSessionStatus.Connected.value && stored.expiresAt > 0 && stored.expiresAt < nowMillis()) {
            store.markNeedsReconnect(userId)
            return stored.copy(status = BiliBiliSessionStatus.NeedsReconnect.value)
        }
        return stored
    }

    suspend fun healthCheck(userId: String): BiliBiliHealthResult {
        if (!isConfigured) return BiliBiliHealthResult.Unconfigured
        val cookies = connectedCookies(userId) ?: return BiliBiliHealthResult.Disconnected
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(NAV_API_URL)
                .header("Cookie", cookies)
                .header("User-Agent", BiliBiliQrLoginService.WEB_USER_AGENT)
                .header("Referer", "https://www.bilibili.com/")
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.code == 412) return@runCatching BiliBiliHealthResult.RateLimited
                    val body = response.body?.string() ?: return@runCatching BiliBiliHealthResult.Error("Empty response")
                    val root = json.parseToJsonElement(body).jsonObject
                    val code = root["code"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
                    when (code) {
                        0 -> BiliBiliHealthResult.Healthy
                        -101 -> BiliBiliHealthResult.Expired
                        -352 -> BiliBiliHealthResult.RateLimited
                        else -> BiliBiliHealthResult.Error("Unexpected code: $code")
                    }
                }
            }.getOrElse { BiliBiliHealthResult.Error(it.message ?: "Network error") }
        }
    }

    suspend fun delete(userId: String): Boolean = store.delete(userId)

    suspend fun connectedCookies(userId: String): String? {
        val crypto = crypto ?: return null
        val encrypted = store.connectedEncrypted(userId) ?: return null
        val decrypted = runCatching { crypto.decrypt(encrypted) }.getOrNull()
        if (decrypted == null) store.markNeedsReconnect(userId)
        return decrypted
    }

    suspend fun markUsed(userId: String) = store.markUsed(userId)

    suspend fun markNeedsReconnect(userId: String) = store.markNeedsReconnect(userId)

    private fun parseSessDataExpiry(cookieHeader: String): Long? {
        val sessData = cookieHeader.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("SESSDATA=", ignoreCase = true) }
            ?.substringAfter("=")
            ?: return null
        val parts = sessData.split(",")
        val timestamp = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: return null
        if (timestamp < 1_000_000_000L) return null
        return timestamp * 1000
    }

    private fun estimateExpiry(): Long = nowMillis() + DEFAULT_COOKIE_TTL_MS

    companion object {
        private const val DEFAULT_COOKIE_TTL_MS = 30L * 24 * 60 * 60 * 1000
        private const val NAV_API_URL = "https://api.bilibili.com/x/web-interface/nav"
    }
}

sealed class BiliBiliHealthResult {
    object Healthy : BiliBiliHealthResult()
    object Expired : BiliBiliHealthResult()
    object RateLimited : BiliBiliHealthResult()
    object Disconnected : BiliBiliHealthResult()
    object Unconfigured : BiliBiliHealthResult()
    data class Error(val message: String) : BiliBiliHealthResult()
}

sealed class BiliBiliQrLoginResult {
    data class Success(val response: BiliBiliQrLoginResponse) : BiliBiliQrLoginResult()
    data class Error(val message: String) : BiliBiliQrLoginResult()
    object Unavailable : BiliBiliQrLoginResult()
}
