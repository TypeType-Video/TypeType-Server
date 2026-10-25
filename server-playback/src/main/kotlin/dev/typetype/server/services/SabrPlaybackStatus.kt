package dev.typetype.server.services

import java.util.concurrent.atomic.AtomicReference

class SabrPlaybackStatus {
    private val state = AtomicReference(SabrPlaybackState.IDLE)
    private val terminalError = AtomicReference<String?>()
    private val networkError = AtomicReference<String?>()
    private val terminalKind = AtomicReference<SabrFailureKind?>()

    fun transition(next: SabrPlaybackState): Unit = synchronized(this) {
        if (!state.get().isFatal()) state.set(next)
    }

    fun state(): SabrPlaybackState = state.get()

    fun recordNetworkFailure(message: String?): Unit = synchronized(this) {
        if (state.get().isFatal()) return@synchronized
        networkError.set(message ?: "SABR network failure")
        state.set(SabrPlaybackState.NETWORK_FAILED)
    }

    fun networkFailure(): String? = networkError.get()

    fun failTerminal(message: String?, kind: SabrFailureKind = SabrFailureKind.UNKNOWN): Unit = synchronized(this) {
        if (state.get().isFatal()) return@synchronized
        terminalError.set(message ?: "SABR terminal failure")
        terminalKind.set(kind)
        state.set(SabrPlaybackState.TERMINAL)
    }

    fun terminalFailure(): String? = terminalError.get()

    fun terminalFailureKind(): SabrFailureKind? = terminalKind.get()

    private fun SabrPlaybackState.isFatal(): Boolean =
        this == SabrPlaybackState.TERMINAL || this == SabrPlaybackState.NETWORK_FAILED
}
