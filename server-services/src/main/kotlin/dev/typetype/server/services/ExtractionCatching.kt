package dev.typetype.server.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

suspend inline fun <T> runExtractionCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(error)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
