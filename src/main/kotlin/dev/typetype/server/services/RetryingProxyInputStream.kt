package dev.typetype.server.services

import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Objects

internal class RetryingProxyInputStream(
    private val execute: (Request) -> Response,
    private val request: Request,
    initialResponse: Response,
    private var attemptsRemaining: Int,
    private val checkActive: () -> Unit = {},
) : InputStream() {
    private var response = initialResponse
    private var source = response.body.byteStream()
    private val status = response.code
    private val length = response.body.contentLength()
    private val headers = listOf("Content-Range", "Content-Type", "Content-Encoding", "ETag")
        .associateWith(response::header)
    private var digest = if (response.header("ETag")?.let {
        it.length >= 2 && it.startsWith('"') && it.endsWith('"')
    } == true) {
        null
    } else {
        MessageDigest.getInstance("SHA-256")
    }
    private var delivered = 0L
    private var closed = false
    private val singleByte = ByteArray(1)

    override fun read(): Int = if (read(singleByte, 0, 1) == -1) -1 else singleByte[0].toInt() and 0xff

    override fun read(bytes: ByteArray, offset: Int, count: Int): Int {
        Objects.checkFromIndexSize(offset, count, bytes.size)
        checkOpen()
        if (count == 0) return 0
        if (length >= 0 && delivered == length) return -1
        while (true) {
            try {
                val size = if (length < 0) count else minOf(count.toLong(), length - delivered).toInt()
                val read = source.read(bytes, offset, size)
                if (read < 0 && length >= 0 && delivered < length) throw EOFException("Incomplete proxy body")
                if (read > 0) {
                    digest?.update(bytes, offset, read)
                    delivered += read
                }
                return read
            } catch (error: IOException) {
                recover(error)
            }
        }
    }

    private fun recover(initialError: IOException) {
        response.close()
        val expectedPrefix = digest?.digest()
        var lastError = initialError
        while (attemptsRemaining > 0) {
            checkOpen()
            if (Thread.currentThread().isInterrupted) throw lastError
            attemptsRemaining--
            var candidate: Response? = null
            try {
                val opened = execute(request)
                candidate = opened
                if (!opened.isSuccessful) throw IOException("Upstream returned ${opened.code}")
                if (opened.code != status || opened.body.contentLength() != length ||
                    headers.any { (name, value) -> opened.header(name) != value }
                ) throw ChangedProxyBodyException()
                val nextSource = candidate.body.byteStream()
                val nextDigest = expectedPrefix?.let { MessageDigest.getInstance("SHA-256") }
                verifyPrefix(nextSource, nextDigest, expectedPrefix)
                response = candidate
                source = nextSource
                digest = nextDigest
                candidate = null
                return
            } catch (error: IOException) {
                if (error is ChangedProxyBodyException) throw error
                lastError = error
            } finally {
                candidate?.close()
            }
        }
        throw lastError
    }

    private fun verifyPrefix(input: InputStream, nextDigest: MessageDigest?, expected: ByteArray?) {
        // Replaying the original range also works when the CDN supplies no strong validator.
        val buffer = ByteArray(64 * 1024)
        val verificationDigest = expected?.let { MessageDigest.getInstance("SHA-256") }
        var remaining = delivered
        while (remaining > 0) {
            checkOpen()
            val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (count < 0) throw EOFException("Incomplete proxy retry prefix")
            nextDigest?.update(buffer, 0, count)
            verificationDigest?.update(buffer, 0, count)
            remaining -= count
        }
        if (expected != null && !MessageDigest.isEqual(expected, verificationDigest?.digest())) {
            throw ChangedProxyBodyException()
        }
    }

    private fun checkOpen() {
        if (closed) throw IOException("Proxy stream is closed")
        checkActive()
    }

    override fun close() {
        if (closed) return
        closed = true
        response.close()
    }
}

private class ChangedProxyBodyException : IOException("Upstream proxy body changed during retry")
