package dev.typetype.server.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.ServiceList

class BiliBiliSessionScopeTest {
    @Test
    fun `withCredentials sets and clears tokens`() = runBlocking {
        val bilibili = ServiceList.BiliBili
        BiliBiliSessionScope.withCredentials("SESSDATA=test; bili_jct=csrf") {
            assertEquals("SESSDATA=test; bili_jct=csrf", bilibili.tokens)
        }
        assertEquals("", bilibili.tokens)
    }

    @Test
    fun `withoutCredentials clears tokens`() = runBlocking {
        val bilibili = ServiceList.BiliBili
        bilibili.setTokens("leftover")
        BiliBiliSessionScope.withoutCredentials {
            assertEquals("", bilibili.tokens)
        }
        assertEquals("", bilibili.tokens)
    }
}
