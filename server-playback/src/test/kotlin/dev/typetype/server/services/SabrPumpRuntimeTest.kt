package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SabrPumpPolicyTest {
    @Test
    fun `live demand only waits at the actual edge`() {
        assertEquals(LIVE_EDGE_POLL_MS, SabrPumpPolicy.demandDelayMs(100L, 0L, activeLive = true, futureDemand = false))
        assertEquals(LIVE_EDGE_POLL_MS, SabrPumpPolicy.demandDelayMs(100L, 0L, activeLive = true, futureDemand = true))
        assertEquals(LIVE_EDGE_POLL_MS, SabrPumpPolicy.demandDelayMs(100L, 0L, activeLive = true, futureDemand = null))
        assertEquals(LIVE_EDGE_POLL_MS, SabrPumpPolicy.demandDelayMs(100L, 0L, activeLive = false, futureDemand = false))
        assertEquals(100L, SabrPumpPolicy.demandDelayMs(100L, 0L, activeLive = false, futureDemand = null))
    }
}
