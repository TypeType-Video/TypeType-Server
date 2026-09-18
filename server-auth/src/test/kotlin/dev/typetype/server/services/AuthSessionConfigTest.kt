package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AuthSessionConfigTest {
    @Test
    fun `defaults keep secure thirty day sessions`() {
        val config = AuthSessionConfig.fromEnvironment { null }

        assertEquals(30L, config.refreshTtlDays)
        assertFalse(config.allowInsecureCookies)
    }

    @Test
    fun `session duration is bounded`() {
        val belowMinimum = AuthSessionConfig.fromEnvironment { name ->
            if (name == "AUTH_SESSION_TTL_DAYS") "0" else null
        }
        val aboveMaximum = AuthSessionConfig.fromEnvironment { name ->
            if (name == "AUTH_SESSION_TTL_DAYS") "900" else null
        }

        assertEquals(1L, belowMinimum.refreshTtlDays)
        assertEquals(365L, aboveMaximum.refreshTtlDays)
    }

    @Test
    fun `insecure cookies require an explicit enabled value`() {
        val disabled = AuthSessionConfig.fromEnvironment { "unexpected" }
        val enabled = AuthSessionConfig.fromEnvironment { name ->
            if (name == "AUTH_ALLOW_INSECURE_COOKIES") "yes" else null
        }

        assertFalse(disabled.allowInsecureCookies)
        assertTrue(enabled.allowInsecureCookies)
    }
}
