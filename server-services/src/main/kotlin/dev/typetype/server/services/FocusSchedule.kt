package dev.typetype.server.services

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

object FocusSchedule {
    const val ALWAYS = "always"
    const val WEEKDAYS = "weekdays"

    fun isValid(schedule: String, timezone: String?): Boolean = when (schedule) {
        ALWAYS -> true
        WEEKDAYS -> timezone != null && zone(timezone) != null
        else -> false
    }

    fun isActive(schedule: String, timezone: String?, now: Long): Boolean {
        if (schedule != WEEKDAYS) return true
        val zone = timezone?.let(::zone) ?: return true
        val day = Instant.ofEpochMilli(now).atZone(zone).dayOfWeek
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
    }

    private fun zone(id: String): ZoneId? = runCatching { ZoneId.of(id) }.getOrNull()
}
