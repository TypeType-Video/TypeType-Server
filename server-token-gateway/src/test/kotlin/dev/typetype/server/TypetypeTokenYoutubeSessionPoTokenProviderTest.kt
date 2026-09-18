package dev.typetype.server

import dev.typetype.server.services.TypetypeTokenYoutubeSessionPoTokenProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class TypetypeTokenYoutubeSessionPoTokenProviderTest {
    private val localization = Localization("en", "US")
    private val country = ContentCountry("US")

    @AfterEach
    fun clearCredentials(): Unit = ServiceList.YouTube.setTokens("")

    @Test
    fun `does not mint a session token for anonymous extraction`() {
        var calls = 0
        val provider = provider(
            boundTokenFetcher = { calls += 1; "unused" },
            visitorDataFetcher = { _, _ -> calls += 1; "unused" },
        )

        val result = provider.getSessionPoToken("WEB", "1.0", "test-user-agent", localization, country, false)

        assertNull(result)
        assertEquals(0, calls)
    }

    @Test
    fun `binds and caches the player token to authenticated visitor data`() {
        ServiceList.YouTube.setTokens("SID=one; SAPISID=one")
        var visitorCalls = 0
        val bindings = mutableListOf<String>()
        val provider = provider(
            boundTokenFetcher = { bindings += it; "token-for-$it" },
            visitorDataFetcher = { _, _ -> visitorCalls += 1; "visitor-one" },
        )

        val first = provider.getSessionPoToken("TV", "1.0", "test-user-agent", localization, country, true)
        val second = provider.getSessionPoToken("WEB", "2.0", "test-user-agent", localization, country, true)

        assertEquals("visitor-one", first?.visitorData)
        assertEquals("token-for-visitor-one", first?.poToken)
        assertEquals(first?.poToken, second?.poToken)
        assertEquals(listOf("visitor-one"), bindings)
        assertEquals(1, visitorCalls)
    }

    @Test
    fun `invalidates cached token when credentials change`() {
        var index = 0
        val provider = provider(
            boundTokenFetcher = { "token-for-$it" },
            visitorDataFetcher = { _, _ -> "visitor-${++index}" },
        )
        ServiceList.YouTube.setTokens("SID=one; SAPISID=one")
        val first = provider.getSessionPoToken("WEB", "1.0", "test-user-agent", localization, country, true)
        ServiceList.YouTube.setTokens("SID=two; SAPISID=two")

        val second = provider.getSessionPoToken("WEB", "1.0", "test-user-agent", localization, country, true)

        assertEquals("visitor-1", first?.visitorData)
        assertEquals("visitor-2", second?.visitorData)
    }

    private fun provider(
        boundTokenFetcher: (String) -> String?,
        visitorDataFetcher: (Localization, ContentCountry) -> String,
    ): TypetypeTokenYoutubeSessionPoTokenProvider = TypetypeTokenYoutubeSessionPoTokenProvider(
        boundTokenFetcher,
        visitorDataFetcher,
    )
}
