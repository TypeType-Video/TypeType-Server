package dev.typetype.server.services

import dev.typetype.server.models.BiliBiliQrLoginResponse
import dev.typetype.server.models.BiliBiliQrPollResponse
import dev.typetype.server.models.BiliBiliSessionStatusResponse

class BiliBiliSessionService(
    private val crypto: BiliBiliSessionCrypto?,
    private val store: BiliBiliSessionStore = BiliBiliSessionStore(),
    private val qrLoginService: BiliBiliQrLoginService = BiliBiliQrLoginService(),
) {
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
                    expiresAt = estimateExpiry(),
                )
                BiliBiliQrPollResponse("confirmed")
            }
            is BiliBiliQrPollResult.Scanned -> BiliBiliQrPollResponse("scanned")
            is BiliBiliQrPollResult.Waiting -> BiliBiliQrPollResponse("waiting")
            is BiliBiliQrPollResult.Expired -> BiliBiliQrPollResponse("expired")
            is BiliBiliQrPollResult.Error -> BiliBiliQrPollResponse("error", result.message)
        }
    }

    suspend fun status(userId: String): BiliBiliSessionStatusResponse =
        if (isConfigured) store.status(userId)
        else BiliBiliSessionStatusResponse(BiliBiliSessionStatus.Disconnected.value, 0, 0)

    suspend fun delete(userId: String): Boolean = store.delete(userId)

    suspend fun connectedCookies(userId: String): String? {
        val crypto = crypto ?: return null
        val encrypted = store.connectedEncrypted(userId) ?: return null
        val decrypted = runCatching { crypto.decrypt(encrypted) }.getOrNull()
        if (decrypted == null) store.markNeedsReconnect(userId)
        return decrypted
    }

    private fun estimateExpiry(): Long = System.currentTimeMillis() + DEFAULT_COOKIE_TTL_MS

    companion object {
        private const val DEFAULT_COOKIE_TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}

sealed class BiliBiliQrLoginResult {
    data class Success(val response: BiliBiliQrLoginResponse) : BiliBiliQrLoginResult()
    data class Error(val message: String) : BiliBiliQrLoginResult()
    object Unavailable : BiliBiliQrLoginResult()
}
