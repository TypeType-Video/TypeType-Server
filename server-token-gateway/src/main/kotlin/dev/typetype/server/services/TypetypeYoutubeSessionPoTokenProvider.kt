package dev.typetype.server.services

import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider

object TypetypeYoutubeSessionPoTokenProvider : YoutubeSessionPoTokenProvider {
    private val scopedToken = ThreadLocal<YoutubeSessionPoToken>()
    @Volatile private var authenticatedProvider: YoutubeSessionPoTokenProvider? = null

    fun configureAuthenticatedProvider(provider: YoutubeSessionPoTokenProvider?): Unit {
        authenticatedProvider = provider
    }

    fun <T> withToken(token: SabrTokenBundle, block: () -> T): T {
        return withToken(token.youtubeSessionPoToken(), block)
    }

    fun <T> withToken(token: YoutubeSessionPoToken, block: () -> T): T {
        val previous = scopedToken.get()
        scopedToken.set(token)
        return try {
            block()
        } finally {
            if (previous == null) scopedToken.remove() else scopedToken.set(previous)
        }
    }

    override fun getSessionPoToken(
        clientName: String,
        clientVersion: String,
        userAgent: String?,
        localization: Localization,
        contentCountry: ContentCountry,
        loggedIn: Boolean,
    ): YoutubeSessionPoToken? = scopedToken.get()
        ?: authenticatedProvider?.getSessionPoToken(
            clientName,
            clientVersion,
            userAgent,
            localization,
            contentCountry,
            loggedIn,
        )
}
