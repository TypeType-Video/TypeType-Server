package dev.typetype.server

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.ConsoleAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class LogbackAsyncConsoleTest {
    @Test
    fun `console output goes through a non blocking async appender`() {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val root = context.getLogger(Logger.ROOT_LOGGER_NAME)

        val async = root.getAppender("ASYNC_STDOUT") as AsyncAppender

        assertTrue(async.isNeverBlock)
        assertEquals(8192, async.queueSize)
        assertTrue(async.getAppender("STDOUT") is ConsoleAppender<*>)
        assertEquals(null, root.getAppender("STDOUT"))
    }
}
