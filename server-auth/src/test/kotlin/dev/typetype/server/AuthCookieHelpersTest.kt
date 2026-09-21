package dev.typetype.server

import dev.typetype.server.services.AuthCookieHelpers
import dev.typetype.server.services.AuthSessionConfig
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthCookieHelpersTest {
    @Test
    fun `extract refresh token from cookie header`() = testApplication {
        application {
            routing {
                get("/probe") {
                    call.respondText(AuthCookieHelpers.extractRefreshToken(call) ?: "none")
                }
            }
        }
        val response = client.get("/probe") {
            header(HttpHeaders.Cookie, "foo=bar; refresh_token=abc123; x=y")
        }
        assertEquals("abc123", response.bodyAsText())
    }

    @Test
    fun `secure refresh cookie remains the default`() = testApplication {
        application {
            routing {
                get("/probe") {
                    AuthCookieHelpers.setRefreshCookie(call.response, "abc123", AuthSessionConfig(refreshTtlDays = 45))
                    call.respondText("ok")
                }
            }
        }

        val cookie = client.get("/probe").headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString("; ")
        assertTrue(cookie.contains("Max-Age=3888000"))
        assertTrue(cookie.contains("Secure"))
        assertTrue(cookie.contains("SameSite=None"))
    }

    @Test
    fun `explicit http compatibility cookie uses lax same site`() = testApplication {
        application {
            routing {
                get("/probe") {
                    val config = AuthSessionConfig(refreshTtlDays = 7, allowInsecureCookies = true)
                    AuthCookieHelpers.setRefreshCookie(call.response, "abc123", config)
                    call.respondText("ok")
                }
            }
        }

        val cookie = client.get("/probe").headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString("; ")
        assertTrue(cookie.contains("Max-Age=604800"))
        assertFalse(cookie.contains("Secure"))
        assertTrue(cookie.contains("SameSite=Lax"))
    }
}
