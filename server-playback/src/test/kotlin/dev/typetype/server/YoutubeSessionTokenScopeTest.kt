package dev.typetype.server

import dev.typetype.server.downloader.YoutubeAuthUserContext
import dev.typetype.server.services.YoutubeSessionCredentials
import dev.typetype.server.services.YoutubeSessionTokenScope
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.ServiceList

class YoutubeSessionTokenScopeTest {
    @Test
    fun `withCredentials injects and clears YouTube tokens`() = runBlocking {
        val youtube = ServiceList.YouTube
        youtube.setTokens("previous-cookies")
        val observed = YoutubeSessionTokenScope.withCredentials(
            YoutubeSessionCredentials(
                userId = TEST_USER_ID,
                fingerprint = "session-fingerprint",
                cookies = "SID=session-cookie",
                poToken = "session-pot-value",
                authUser = 2,
            )
        ) {
            youtube.tokens to YoutubeAuthUserContext.headerFor("https://www.youtube.com/youtubei/v1/player")
        }
        assertEquals("SID=session-cookie", observed.first)
        assertEquals("2", observed.second)
        assertEquals("", youtube.tokens.orEmpty())
        assertEquals(null, YoutubeAuthUserContext.headerFor("https://www.youtube.com/youtubei/v1/player"))
    }

    @Test
    fun `withoutCredentials clears YouTube tokens for public extraction`() = runBlocking {
        val youtube = ServiceList.YouTube
        youtube.setTokens("previous-cookies")
        val observed = YoutubeSessionTokenScope.withoutCredentials { youtube.tokens.orEmpty() }
        assertEquals("", observed)
    }
}
