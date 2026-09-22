package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SabrPumpRuntimeTargetedShapeTest {
    @Test
    fun `unresolved demand switches to targeted requests after readvertisement`() {
        val runtime = SabrPumpRuntime()

        assertFalse(runtime.demandNeedsTargetedShape("demand"))
        runtime.beginDemand("demand")
        assertFalse(runtime.demandNeedsTargetedShape("demand"))

        assertEquals(
            SabrDemandRecoveryAction.READVERTISE_TRACK,
            runtime.demandRecoveryAction("demand", requestPerformed = true, resolved = false),
        )
        assertTrue(runtime.demandNeedsTargetedShape("demand"))
        assertEquals(
            SabrDemandRecoveryAction.WAIT,
            runtime.demandRecoveryAction("demand", requestPerformed = true, resolved = false),
        )
        assertTrue(runtime.demandNeedsTargetedShape("demand"))

        runtime.finishDemand("demand")
        assertFalse(runtime.demandNeedsTargetedShape("demand"))
    }

    @Test
    fun `a new demand starts with continuation requests`() {
        val runtime = SabrPumpRuntime()

        runtime.beginDemand("first")
        runtime.demandRecoveryAction("first", requestPerformed = true, resolved = false)
        runtime.beginDemand("second")

        assertFalse(runtime.demandNeedsTargetedShape("first"))
        assertFalse(runtime.demandNeedsTargetedShape("second"))
    }
}
