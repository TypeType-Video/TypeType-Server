package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoToken
import org.schabi.newpipe.extractor.services.youtube.YoutubeSessionPoTokenProvider

class TypetypeYoutubeSessionPoTokenProviderTest {
    @AfterEach
    fun clearAuthenticatedProvider(): Unit =
        TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(null)

    @Test
    fun `exposes the session token only inside its scope`() {
        TypetypeYoutubeSessionPoTokenProvider.withToken(token("visitor", "player-token")) {
            assertEquals("visitor", currentToken()?.visitorData)
            assertEquals("player-token", currentToken()?.poToken)
        }

        assertNull(currentToken())
    }

    @Test
    fun `restores the outer token after a nested scope`() {
        TypetypeYoutubeSessionPoTokenProvider.withToken(token("outer", "outer-token")) {
            TypetypeYoutubeSessionPoTokenProvider.withToken(token("inner", "inner-token")) {
                assertEquals("inner", currentToken()?.visitorData)
            }
            assertEquals("outer", currentToken()?.visitorData)
        }

        assertNull(currentToken())
    }

    @Test
    fun `clears the token when the scoped call fails`() {
        runCatching {
            TypetypeYoutubeSessionPoTokenProvider.withToken(token("visitor", "player-token")) {
                error("failed")
            }
        }

        assertNull(currentToken())
    }

    @Test
    fun `uses the authenticated provider outside a SABR scope`() {
        TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(provider("auth", "auth-token"))

        assertEquals("auth", currentToken()?.visitorData)
        assertEquals("auth-token", currentToken()?.poToken)
    }

    @Test
    fun `prefers the SABR token over the authenticated provider`() {
        TypetypeYoutubeSessionPoTokenProvider.configureAuthenticatedProvider(provider("auth", "auth-token"))

        TypetypeYoutubeSessionPoTokenProvider.withToken(token("sabr", "sabr-token")) {
            assertEquals("sabr", currentToken()?.visitorData)
            assertEquals("sabr-token", currentToken()?.poToken)
        }
    }

    private fun currentToken() = TypetypeYoutubeSessionPoTokenProvider.getSessionPoToken(
        "MWEB",
        "2.20260801.00.00",
        "test-user-agent",
        Localization("en", "US"),
        ContentCountry("US"),
        false,
    )

    private fun token(visitorData: String, playerToken: String) = SabrTokenBundle(
        videoId = "video",
        visitorBoundPoToken = playerToken,
        visitorBoundPoTokenBytes = byteArrayOf(1),
        visitorData = visitorData,
        videoBoundPoToken = "video-token",
        videoBoundPoTokenBytes = byteArrayOf(2),
    )

    private fun provider(visitorData: String, poToken: String) = object : YoutubeSessionPoTokenProvider {
        override fun getSessionPoToken(
            clientName: String,
            clientVersion: String,
            userAgent: String?,
            localization: Localization,
            contentCountry: ContentCountry,
            loggedIn: Boolean,
        ) = YoutubeSessionPoToken(visitorData, poToken)
    }
}
