package dev.typetype.server.services

import dev.typetype.server.models.YoutubeSessionCompleteRequest
import dev.typetype.server.models.YoutubeSessionPairingResponse
import dev.typetype.server.models.YoutubeSessionStatusResponse

class YoutubeSessionService(
    private val crypto: YoutubeSessionCrypto?,
    private val pairingStore: YoutubeSessionPairingStore = YoutubeSessionPairingStore(),
    private val store: YoutubeSessionStore = YoutubeSessionStore(),
) {
    val isConfigured: Boolean = crypto != null

    suspend fun createPairing(userId: String): YoutubeSessionPairingResponse =
        pairingStore.create(userId)

    suspend fun complete(request: YoutubeSessionCompleteRequest): YoutubeSessionCompleteResult {
        val crypto = crypto ?: return YoutubeSessionCompleteResult.Unavailable
        val code = request.code.trim().uppercase()
        val cookies = YoutubeSessionCookieNormalizer.normalize(request.cookies)
            ?: return YoutubeSessionCompleteResult.InvalidCredentials
        val poToken = request.poToken.trim()
        if (code.isBlank() || !validCredentials(cookies, poToken, request.authUser)) {
            return YoutubeSessionCompleteResult.InvalidCredentials
        }
        return store.complete(
            code = code,
            encryptedCookies = crypto.encrypt(cookies),
            encryptedPoToken = crypto.encrypt(poToken),
            authUser = request.authUser,
        )
    }

    suspend fun completeRemote(
        userId: String,
        rawCookies: String,
        rawPoToken: String,
        authUser: Int = 0,
    ): YoutubeSessionCompleteResult {
        val crypto = crypto ?: return YoutubeSessionCompleteResult.Unavailable
        val cookies = YoutubeSessionCookieNormalizer.normalize(rawCookies)
            ?: return YoutubeSessionCompleteResult.InvalidCredentials
        val poToken = rawPoToken.trim()
        if (!validCredentials(cookies, poToken, authUser)) {
            return YoutubeSessionCompleteResult.InvalidCredentials
        }
        store.completeForUser(
            userId = userId,
            encryptedCookies = crypto.encrypt(cookies),
            encryptedPoToken = crypto.encrypt(poToken),
            authUser = authUser,
        )
        return YoutubeSessionCompleteResult.Completed
    }

    suspend fun status(userId: String): YoutubeSessionStatusResponse =
        if (isConfigured) store.status(userId) else YoutubeSessionStatusResponse(YoutubeSessionStatus.Disconnected.value, 0, 0)

    suspend fun delete(userId: String): Boolean = store.delete(userId)

    suspend fun connectedCredentials(userId: String): YoutubeSessionCredentials? {
        val crypto = crypto ?: return null
        val encrypted = store.connectedEncrypted(userId) ?: return null
        val credentials = runCatching {
            YoutubeSessionCredentials(
                userId = userId,
                fingerprint = PublicCacheKey.of(
                    "youtube-session",
                    encrypted.cookies,
                    encrypted.poToken,
                    encrypted.authUser.toString(),
                ),
                cookies = crypto.decrypt(encrypted.cookies),
                poToken = crypto.decrypt(encrypted.poToken),
                authUser = encrypted.authUser,
            )
        }.getOrNull()
        if (credentials == null) store.markNeedsReconnect(userId)
        return credentials
    }

    suspend fun markUsed(userId: String): Unit = store.markUsed(userId)

    suspend fun markNeedsReconnect(userId: String): Unit = store.markNeedsReconnect(userId)

    private fun validCredentials(cookies: String, poToken: String, authUser: Int): Boolean =
        authUser in 0..MAX_AUTH_USER && YoutubeSessionCredentialValidator.isValid(cookies, poToken)

    private companion object {
        const val MAX_AUTH_USER = 99
    }
}
