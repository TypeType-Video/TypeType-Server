package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class FocusScheduleTest {
    private val paris = "Europe/Paris"

    private fun at(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), ZoneId.of(paris)).toInstant().toEpochMilli()

    @Test
    fun `weekdays schedule pauses focus on the weekend in the saved timezone`() {
        assertTrue(FocusSchedule.isActive(FocusSchedule.WEEKDAYS, paris, at("2026-11-13T23:30")))
        assertFalse(FocusSchedule.isActive(FocusSchedule.WEEKDAYS, paris, at("2026-11-14T00:30")))
        assertFalse(FocusSchedule.isActive(FocusSchedule.WEEKDAYS, paris, at("2026-11-15T23:30")))
        assertTrue(FocusSchedule.isActive(FocusSchedule.WEEKDAYS, paris, at("2026-11-16T00:30")))
        assertTrue(FocusSchedule.isActive(FocusSchedule.ALWAYS, paris, at("2026-11-14T12:00")))
    }

    @Test
    fun `weekdays schedule needs a real timezone`() {
        assertTrue(FocusSchedule.isValid(FocusSchedule.ALWAYS, null))
        assertTrue(FocusSchedule.isValid(FocusSchedule.WEEKDAYS, paris))
        assertFalse(FocusSchedule.isValid(FocusSchedule.WEEKDAYS, null))
        assertFalse(FocusSchedule.isValid(FocusSchedule.WEEKDAYS, "Mars/Olympus"))
        assertFalse(FocusSchedule.isValid("sometimes", paris))
    }
}
