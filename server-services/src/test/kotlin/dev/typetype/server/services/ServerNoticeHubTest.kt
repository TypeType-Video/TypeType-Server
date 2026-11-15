package dev.typetype.server.services

import dev.typetype.server.models.ServerNoticeRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ServerNoticeHubTest {
    private val hub = ServerNoticeHub(bootId = "boot-1", now = { 42L })

    @Test
    fun `a new subscriber is greeted with the boot id and the active notice`() = runBlocking {
        hub.announce(ServerNoticeRequest(kind = "update", message = " New version ", resumeInSeconds = 30))

        val hello = (hub.subscribe().first() as ServerEvent.Hello).hello

        assertEquals("boot-1", hello.bootId)
        assertEquals("update", hello.notice?.kind)
        assertEquals("New version", hello.notice?.message)
        assertEquals(30, hello.notice?.resumeInSeconds)
        assertEquals(42L, hello.notice?.issuedAt)
    }

    @Test
    fun `rejects unknown kinds, oversized messages and absurd delays`() {
        assertNull(hub.announce(ServerNoticeRequest(kind = "party")))
        assertNull(hub.announce(ServerNoticeRequest(kind = "update", message = "x".repeat(281))))
        assertNull(hub.announce(ServerNoticeRequest(kind = "update", resumeInSeconds = -1)))
        assertNull(hub.hello().notice)
    }

    @Test
    fun `clearing removes the active notice`() {
        hub.announce(ServerNoticeRequest(kind = "maintenance"))
        hub.clear()

        assertNull(hub.hello().notice)
    }

    @Test
    fun `shutdown leaves a restart notice for late subscribers`() {
        hub.shutdown()

        assertEquals("restart", hub.hello().notice?.kind)
    }
}
