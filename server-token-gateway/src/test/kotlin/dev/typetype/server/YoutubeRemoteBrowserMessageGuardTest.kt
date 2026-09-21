package dev.typetype.server

import dev.typetype.server.services.YoutubeRemoteBrowserMessageGuard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class YoutubeRemoteBrowserMessageGuardTest {
    @Test
    fun `frontend messages allow only bounded remote browser input`() {
        val resize = """{"type":"resize","width":1280,"height":720}"""
        val pointer = """{"type":"pointer","event":"move","x":420,"y":240,"button":"left"}"""
        val key = """{"type":"key","event":"down","key":"A"}"""

        assertEquals(resize, YoutubeRemoteBrowserMessageGuard.frontendText(resize, 4096))
        assertEquals(pointer, YoutubeRemoteBrowserMessageGuard.frontendText(pointer, 4096))
        assertEquals(key, YoutubeRemoteBrowserMessageGuard.frontendText(key, 4096))
        assertNull(YoutubeRemoteBrowserMessageGuard.frontendText("""{"type":"resize","width":9000,"height":720}""", 4096))
        assertNull(YoutubeRemoteBrowserMessageGuard.frontendText("""{"type":"text","value":"secret"}""", 8))
    }

    @Test
    fun `token messages never forward credentials to frontend`() {
        val status = """{"type":"status","phase":"awaiting_login"}"""
        val error = """{"type":"error","message":"Session expired"}"""
        val complete = """{"type":"complete","cookies":"SID=secret","poToken":"secret"}"""

        assertEquals(status, YoutubeRemoteBrowserMessageGuard.tokenText(status))
        assertEquals(error, YoutubeRemoteBrowserMessageGuard.tokenText(error))
        assertNull(YoutubeRemoteBrowserMessageGuard.tokenText(complete))
        assertNull(YoutubeRemoteBrowserMessageGuard.tokenText("""{"type":"status","phase":"unknown"}"""))
    }

    @Test
    fun `token diagnostics logs are forwarded when bounded`() {
        val log = """{"type":"log","at":1200,"message":"login check url=https://accounts.google.com/signin cookies=google.com=[SID]"}"""

        assertEquals(log, YoutubeRemoteBrowserMessageGuard.tokenText(log))
        assertNull(YoutubeRemoteBrowserMessageGuard.tokenText("""{"type":"log","message":"missing at"}"""))
        assertNull(YoutubeRemoteBrowserMessageGuard.tokenText("""{"type":"log","at":-1,"message":"negative"}"""))
        assertNull(YoutubeRemoteBrowserMessageGuard.tokenText("""{"type":"log","at":5,"message":"${"x".repeat(1001)}"}"""))
    }
}
