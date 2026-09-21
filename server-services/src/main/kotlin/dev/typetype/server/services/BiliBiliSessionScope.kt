package dev.typetype.server.services

import dev.typetype.server.downloader.BilibiliCookieContext
import org.schabi.newpipe.extractor.ServiceList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

object BiliBiliSessionScope {
    private const val PUBLIC_PERMITS = 64
    private val permits = Semaphore(PUBLIC_PERMITS, true)
    private val userRequestTimestamps = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private const val MAX_REQUESTS_PER_WINDOW = 30
    private const val RATE_WINDOW_MS = 60_000L

    suspend fun <T> withCredentials(userId: String, cookieHeader: String, block: suspend () -> T): T {
        checkRateLimit(userId)
        return withPermits(PUBLIC_PERMITS) {
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
        withPermits(1) {
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

    private suspend fun <T> withPermits(count: Int, block: suspend () -> T): T {
        val acquired = AtomicBoolean(false)
        return try {
            withContext(Dispatchers.IO) {
                if (!permits.tryAcquire(count, PERMIT_ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    error("Timed out waiting for BiliBili extraction permits")
                }
                acquired.set(true)
            }
            block()
        } finally {
            if (acquired.get()) permits.release(count)
        }
    }

    private const val PERMIT_ACQUIRE_TIMEOUT_MS = 15_000L
    private val BILIBILI_COOKIE_FUNCTIONS = setOf("high_res", "ai_subtitle")
}

class BiliBiliRateLimitException(val maxRequests: Int) :
    RuntimeException("BiliBili rate limit exceeded: max $maxRequests requests per minute")
