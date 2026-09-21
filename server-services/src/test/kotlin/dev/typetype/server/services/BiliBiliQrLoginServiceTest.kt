package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BiliBiliQrLoginServiceTest {
    @Test
    fun code_86101_is_waiting() {
        assertTrue(classifyBiliBiliQrCode(86101) is BiliBiliQrPollResult.Waiting)
    }

    @Test
    fun code_86090_is_scanned() {
        assertTrue(classifyBiliBiliQrCode(86090) is BiliBiliQrPollResult.Scanned)
    }
}
