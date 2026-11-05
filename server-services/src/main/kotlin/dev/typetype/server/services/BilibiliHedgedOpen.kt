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
) {
    class Opened(val request: Request, val response: Response)

    fun open(execute: (Request) -> Response, primary: Request, alternate: Request?): Opened {
        if (alternate == null) return Opened(primary, execute(primary))
        val first = start(execute, primary)
        val early = try {
            first.get(hedgeAfterMillis, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            null
        } catch (_: ExecutionException) {
            return Opened(alternate, execute(alternate))
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
            val response = execute(request)
            if (response.isSuccessful) {
                Opened(request, response)
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
