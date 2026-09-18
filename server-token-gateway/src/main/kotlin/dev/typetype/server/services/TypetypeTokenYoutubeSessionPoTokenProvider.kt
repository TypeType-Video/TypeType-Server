package dev.typetype.server.services

import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider
import java.security.MessageDigest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class TypetypeTokenYoutubeSessionPoTokenProvider(
    private val boundTokenFetcher: (String) -> String?,
    private val visitorDataFetcher: (Localization, ContentCountry) -> String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : YoutubeSessionPoTokenProvider {
    constructor(tokenServiceUrl: String) : this(
        TypetypeTokenSabrTokenClient(tokenServiceUrl).let { client ->
            { binding -> client.fetchBoundToken(binding) }
        },
        AuthenticatedYoutubeVisitorData::fetch,
    )

    @Volatile private var cached: CachedToken? = null
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<CachedToken?>>()

    override fun getSessionPoToken(
        clientName: String,
        clientVersion: String,
        userAgent: String?,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
    ): YoutubeSessionPoToken? {
        if (!loggedIn) return null
        val credentialIdentity = credentialIdentity(ServiceList.YouTube.tokens)
        cached?.takeIf {
            it.credentialIdentity == credentialIdentity && nowMillis() - it.createdAtMs < TOKEN_TTL_MS
        }?.let { return it.token }
        val pending = CompletableFuture<CachedToken?>()
        val existing = inFlight.putIfAbsent(credentialIdentity, pending)
        if (existing != null) return existing.join()?.token
        return try {
            val visitorData = visitorDataFetcher(localization, contentCountry)
            val token = boundTokenFetcher(visitorData)?.takeIf(String::isNotBlank)
                ?: return null
            val loaded = CachedToken(
                credentialIdentity,
                YoutubeSessionPoToken(visitorData, token),
                nowMillis(),
            )
            cached = loaded
            pending.complete(loaded)
            loaded.token
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            if (!pending.isDone) pending.complete(null)
            inFlight.remove(credentialIdentity, pending)
        }
    }

    private fun credentialIdentity(cookies: String): String = MessageDigest.getInstance("SHA-256")
        .digest(cookies.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private data class CachedToken(
        val credentialIdentity: String,
        val token: YoutubeSessionPoToken,
        val createdAtMs: Long,
    )

    private companion object {
        const val TOKEN_TTL_MS = 6L * 60L * 60L * 1000L

    }
}
