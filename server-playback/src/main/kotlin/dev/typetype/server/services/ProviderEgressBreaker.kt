package dev.typetype.server.services

import org.slf4j.LoggerFactory
import java.util.ArrayDeque

class ProviderEgressBreaker(
    val egress: String = "direct",
    private val threshold: Int = 3,
    private val windowMs: Long = 60_000L,
    private val baseCooldownMs: Long = 30_000L,
    private val maxCooldownMs: Long = 300_000L,
    private val probeTimeoutMs: Long = 30_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val refusals = ArrayDeque<Long>()
    private var state = ProviderEgressBreakerState.CLOSED
    private var openUntilMs = 0L
    private var probeStartedAtMs = 0L
    private var cooldownMs = baseCooldownMs

    @Synchronized
    fun admit(): Long? {
        val time = now()
        return when (state) {
            ProviderEgressBreakerState.CLOSED -> null
            ProviderEgressBreakerState.OPEN -> if (time < openUntilMs) openUntilMs - time else startProbe(time)
            ProviderEgressBreakerState.HALF_OPEN ->
                if (time - probeStartedAtMs >= probeTimeoutMs) startProbe(time) else probeStartedAtMs + probeTimeoutMs - time
        }
    }

    @Synchronized
    fun openRetryAfterMs(): Long? {
        val time = now()
        return (openUntilMs - time).takeIf { state == ProviderEgressBreakerState.OPEN && it > 0L }
    }

    @Synchronized
    fun recordRefusal(): Unit {
        val time = now()
        when (state) {
            ProviderEgressBreakerState.HALF_OPEN -> open(time, (cooldownMs * 2).coerceAtMost(maxCooldownMs))
            ProviderEgressBreakerState.OPEN -> Unit
            ProviderEgressBreakerState.CLOSED -> {
                refusals.addLast(time)
                while (refusals.isNotEmpty() && time - refusals.first() > windowMs) refusals.removeFirst()
                if (refusals.size >= threshold) open(time, baseCooldownMs)
            }
        }
    }

    @Synchronized
    fun recordSuccess(): Unit {
        if (state != ProviderEgressBreakerState.CLOSED) logger.info("provider_breaker event=closed egress={}", egress)
        state = ProviderEgressBreakerState.CLOSED
        refusals.clear()
        cooldownMs = baseCooldownMs
    }

    private fun startProbe(time: Long): Long? {
        state = ProviderEgressBreakerState.HALF_OPEN
        probeStartedAtMs = time
        logger.info("provider_breaker event=half_open egress={}", egress)
        return null
    }

    private fun open(time: Long, cooldown: Long): Unit {
        state = ProviderEgressBreakerState.OPEN
        cooldownMs = cooldown
        openUntilMs = time + cooldown
        refusals.clear()
        logger.warn("provider_breaker event=opened egress={} cooldownMs={}", egress, cooldown)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(ProviderEgressBreaker::class.java)
    }
}
