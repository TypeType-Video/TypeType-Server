package dev.typetype.server.services

import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class BilibiliHedgedOpen(
    private val hedgeAfterMillis: Long = DEFAULT_HEDGE_AFTER_MILLIS,
    private val executor: ExecutorService = SHARED_EXECUTOR,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    class Opened(val request: Request, val response: Response, val durationNanos: Long = 0L)

    fun open(execute: (Request) -> Response, primary: Request, alternate: Request?): Opened {
        if (alternate == null) {
            val started = nanoTime()
            val response = execute(primary)
            return Opened(primary, response, (nanoTime() - started).coerceAtLeast(0))
        }
        val first = start(execute, primary)
        val early = try {
            first.get(hedgeAfterMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (_: ExecutionException) {
            val started = nanoTime()
            val response = execute(alternate)
            return Opened(alternate, response, (nanoTime() - started).coerceAtLeast(0))
        }
        if (early != null) return early
        val second = start(execute, alternate)
        val winner = CompletableFuture<Opened?>()
        listOf(first, second).forEach { attempt ->
            attempt.whenComplete { opened, _ ->
                if (opened == null || !winner.complete(opened)) opened?.response?.close()
            }
        }
        CompletableFuture.allOf(first, second).whenComplete { _, _ -> winner.complete(null) }
        return try {
            winner.get() ?: throw IOException("No Bilibili mirror answered")
        } catch (error: InterruptedException) {
            first.cancel(true)
            second.cancel(true)
            throw error
        }
    }

    private fun start(execute: (Request) -> Response, request: Request): CompletableFuture<Opened?> =
        CompletableFuture.supplyAsync({
            val started = nanoTime()
            val response = execute(request)
            val durationNanos = (nanoTime() - started).coerceAtLeast(0)
            if (response.isSuccessful) {
                Opened(request, response, durationNanos)
            } else {
                response.close()
                null
            }
        }, executor)

    companion object {
        const val DEFAULT_HEDGE_AFTER_MILLIS = 350L
        private val SHARED_EXECUTOR: ExecutorService = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "bilibili-hedged-open").apply { isDaemon = true }
        }
    }
}
