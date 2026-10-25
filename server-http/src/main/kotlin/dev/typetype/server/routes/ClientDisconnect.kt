package dev.typetype.server.routes

import io.ktor.util.cio.ChannelWriteException
import io.ktor.utils.io.ClosedByteChannelException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.channels.ClosedChannelException

internal suspend inline fun stopOnClientDisconnect(block: () -> Unit): Unit {
    try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        if (!error.isClientDisconnect()) throw error
        currentCoroutineContext().ensureActive()
    }
}

fun Throwable.isClientDisconnect(): Boolean {
    var current: Throwable? = this
    var depth = 0
    while (current != null && depth < MAX_CAUSE_DEPTH) {
        if (current.isDisconnectCause()) return true
        current = current.cause?.takeIf { it !== current }
        depth++
    }
    return false
}

private fun Throwable.isDisconnectCause(): Boolean = when (this) {
    is ChannelWriteException,
    is ClosedByteChannelException,
    is ClosedChannelException -> true
    is IOException -> message?.let { text -> DISCONNECT_MESSAGES.any { text.contains(it, ignoreCase = true) } } == true
    else -> false
}

private val DISCONNECT_MESSAGES = listOf("Broken pipe", "Connection reset by peer")

private const val MAX_CAUSE_DEPTH = 16
