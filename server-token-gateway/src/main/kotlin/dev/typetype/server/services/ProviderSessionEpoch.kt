package dev.typetype.server.services

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ProviderSessionEpoch {
    private val epoch = AtomicLong(0L)
    private val lastGeneration = AtomicReference<String?>(null)
    private val lastVisitorData = AtomicReference<String?>(null)

    fun current(): Long = epoch.get()

    fun advance(): Long = epoch.incrementAndGet()

    fun observe(token: SabrTokenBundle, renewed: Boolean = false): Unit {
        val previousVisitor = lastVisitorData.getAndSet(token.visitorData)
        val generation = token.generation
        val changed = if (generation != null) {
            val previous = lastGeneration.getAndSet(generation)
            previous != null && previous != generation
        } else {
            renewed || previousVisitor != null && previousVisitor != token.visitorData
        }
        if (changed) advance()
    }
}
