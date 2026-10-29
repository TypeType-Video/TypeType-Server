package dev.typetype.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeliveredTailTest {
    @Test
    fun `keeps only the last delivered bytes across writes`() {
        val tail = DeliveredTail(4)
        tail.record(byteArrayOf(1, 2, 3), 0, 3)
        tail.record(byteArrayOf(4, 5, 6), 0, 3)

        assertEquals(4, tail.size)
        assertTrue(tail.matches(byteArrayOf(3, 4, 5, 6).inputStream()))
        assertFalse(tail.matches(byteArrayOf(3, 4, 5, 7).inputStream()))
    }

    @Test
    fun `a write larger than the tail keeps its end`() {
        val tail = DeliveredTail(3)
        tail.record(byteArrayOf(9, 1, 2, 3, 4), 1, 4)

        assertTrue(tail.matches(byteArrayOf(2, 3, 4).inputStream()))
    }
}
