package dev.typetype.server.services

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.typetype.server.sabr.SabrNextRequestPolicy
import dev.typetype.server.sabr.YoutubeSabrSession
import dev.typetype.server.sabr.YoutubeSabrStreamState

class SabrPumpRuntimeTest {
    @Test
    fun `startup and seek cushions follow PipePipe policy`() {
        var now = 1_000L
        val holder = holder(policy(targetAudioMs = 4_000, targetVideoMs = 7_000), playerTimeMs = 1_000L)
        val runtime = SabrPumpRuntime { now }

        assertEquals(25_000L, runtime.targetReadaheadCushionMs(holder))
        now += 25_001L
        assertEquals(7_000L, runtime.targetReadaheadCushionMs(holder))

        runtime.activateSeekMode()
        assertEquals(5_000L, runtime.targetReadaheadCushionMs(holder))
        now += 8_000L
        assertEquals(7_000L, runtime.targetReadaheadCushionMs(holder))
    }

    @Test
    fun `readahead cushions scale with playback rate while remaining bounded`() {
        var now = 1_000L
        val holder = holder(
            policy(targetAudioMs = 4_000, targetVideoMs = 7_000),
            playerTimeMs = 1_000L,
            playbackRate = 4.0f,
        )
        val runtime = SabrPumpRuntime { now }

        assertEquals(60_000L, runtime.targetReadaheadCushionMs(holder))
        now += 25_001L
        assertEquals(28_000L, runtime.targetReadaheadCushionMs(holder))
        runtime.activateSeekMode()
        assertEquals(20_000L, runtime.targetReadaheadCushionMs(holder))
    }

    @Test
    fun `server heartbeat bypasses time throttling`() {
        var now = 1_000L
        val holder = holder(
            policy(targetAudioMs = 3_000, targetVideoMs = 3_000, maximumRequestGapMs = 5_000),
            playerTimeMs = 1_000L,
            edgeMs = 13_000L,
        )
        val runtime = SabrPumpRuntime { now }
        now += 25_001L

        assertTrue(runtime.isThrottled(holder))
        runtime.recordRequest()
        now += 4_999L
        assertTrue(runtime.isThrottled(holder))
        now += 1L
        assertFalse(runtime.isThrottled(holder))
    }

    @Test
    fun `startup request caps reported server ahead`() {
        var now = 1_000L
        val holder = holder(policy(), playerTimeMs = 1_000L)
        val runtime = SabrPumpRuntime { now }

        assertEquals(34_000L, runtime.requestPlayerTimeMs(holder, edgeMs = 50_000L))
        now += 25_000L
        assertEquals(1_000L, runtime.requestPlayerTimeMs(holder, edgeMs = 50_000L))
        assertEquals(34_000L, runtime.demandPlayerTimeMs(holder, edgeMs = 50_000L))
    }

    @Test
    fun `slow response without media stays recoverable for watchdog`() {
        var now = 1_000L
        val runtime = SabrPumpRuntime { now }
        runtime.beginDemand("140:44")
        now += SabrPumpPolicy.DEMAND_TARGET_DEADLINE_MS

        assertEquals(SabrDemandRecoveryAction.WAIT, runtime.demandRecoveryAction("140:44", 0, false))
    }

    @Test
    fun `repeated responses without demanded segment stay retryable`() {
        val runtime = SabrPumpRuntime { 1_000L }
        runtime.beginDemand("140:44")

        assertEquals(SabrDemandRecoveryAction.READVERTISE_TRACK, runtime.demandRecoveryAction("140:44", 1, false))
        repeat(5) {
            assertEquals(SabrDemandRecoveryAction.WAIT, runtime.demandRecoveryAction("140:44", 1, false))
        }
    }

    private fun holder(
        policy: SabrNextRequestPolicy,
        playerTimeMs: Long,
        edgeMs: Long = 0L,
        playbackRate: Float = 1.0f,
    ): SabrSessionHolder {
        val holder = mockk<SabrSessionHolder>()
        val session = mockk<YoutubeSabrSession>()
        val state = mockk<YoutubeSabrStreamState>()
        every { holder.session } returns session
        every { holder.playerTimeMs() } returns playerTimeMs
        every { holder.playbackRate() } returns playbackRate
        every { holder.readerTailMs() } returns 1L
        every { session.streamState } returns state
        every { session.cachedBytes } returns 0L
        every { state.getMinBufferedEndMs() } returns edgeMs
        every { state.nextRequestPolicy } returns policy
        return holder
    }

    private fun policy(
        targetAudioMs: Int = -1,
        targetVideoMs: Int = -1,
        maximumRequestGapMs: Int = -1,
    ): SabrNextRequestPolicy {
        val policy = mockk<SabrNextRequestPolicy>()
        every { policy.targetAudioReadaheadMs } returns targetAudioMs
        every { policy.targetVideoReadaheadMs } returns targetVideoMs
        every { policy.maxTimeSinceLastRequestMs } returns maximumRequestGapMs
        return policy
    }
}
