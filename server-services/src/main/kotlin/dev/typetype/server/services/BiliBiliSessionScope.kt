package dev.typetype.server.services

import dev.typetype.server.downloader.BilibiliCookieContext
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.ConcurrentHashMap

object BiliBiliSessionScope {
    private const val PUBLIC_PERMITS = 64
    private val gate = SharedExclusiveGate(
        capacity = PUBLIC_PERMITS,
        maxWaitMs = PERMIT_ACQUIRE_TIMEOUT_MS,
        timeoutMessage = "Timed out waiting for BiliBili extraction permits",
    )
    private val userRequestTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private const val MAX_REQUESTS_PER_WINDOW = 30
    private const val RATE_WINDOW_MS = 60_000L

    suspend fun <T> withCredentials(userId: String, cookieHeader: String, block: suspend () -> T): T {
        checkRateLimit(userId)
        return gate.exclusive {
            val bilibili = ServiceList.BiliBili
            try {
                bilibili.setTokens(cookieHeader)
                bilibili.setCookieFunctions(BILIBILI_COOKIE_FUNCTIONS)
                BilibiliCookieContext.set(cookieHeader)
                block()
            } finally {
                bilibili.setTokens("")
                bilibili.setCookieFunctions(emptySet())
                BilibiliCookieContext.set(null)
            }
        }
    }

    suspend fun <T> withoutCredentials(block: suspend () -> T): T =
        gate.shared {
            val bilibili = ServiceList.BiliBili
            bilibili.setTokens("")
            bilibili.setCookieFunctions(emptySet())
            BilibiliCookieContext.set(null)
            try {
                block()
            } finally {
                BilibiliCookieContext.set(null)
            }
        }

    private fun checkRateLimit(userId: String) {
        val now = System.currentTimeMillis()
        val queue = userRequestTimestamps.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(queue) {
            while (queue.isNotEmpty() && queue.first() < now - RATE_WINDOW_MS) queue.removeFirst()
            if (queue.size >= MAX_REQUESTS_PER_WINDOW) {
                throw BiliBiliRateLimitException(MAX_REQUESTS_PER_WINDOW)
            }
            queue.addLast(now)
        }
    }

    private const val PERMIT_ACQUIRE_TIMEOUT_MS = 15_000L
    private val BILIBILI_COOKIE_FUNCTIONS = setOf("high_res", "ai_subtitle")
}

class BiliBiliRateLimitException(val maxRequests: Int) :
    RuntimeException("BiliBili rate limit exceeded: max $maxRequests requests per minute")
