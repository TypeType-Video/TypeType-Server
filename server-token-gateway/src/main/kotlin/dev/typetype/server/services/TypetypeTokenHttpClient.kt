package dev.typetype.server.services

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object TypetypeTokenHttpClient {
    const val CONNECT_TIMEOUT_MS = 5_000L
    const val READ_TIMEOUT_MS = 20_000L
    const val CALL_TIMEOUT_MS = 20_000L

    fun bounded(base: OkHttpClient = OkHttpClient()): OkHttpClient {
        if (base.callTimeoutMillis > 0) return base
        return base.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }
}
