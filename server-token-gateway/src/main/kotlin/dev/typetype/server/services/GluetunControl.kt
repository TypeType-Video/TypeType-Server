package dev.typetype.server.services

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Duration

/** The two moves the gluetun control server offers: reconnect at random, or connect to a named server. */
class GluetunControl(
    private val controlUrl: String,
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(10)).build(),
) {
    fun reconnect(): Boolean = put("/v1/vpn/status", """{"status":"stopped"}""") &&
        put("/v1/vpn/status", """{"status":"running"}""")

    fun connectTo(server: String): Boolean = put(
        "/v1/vpn/settings",
        """{"provider":{"server_selection":{"names":["${server.replace("\"", "")}"],"countries":[],"cities":[]}}}""",
    )

    private fun put(path: String, body: String): Boolean = runCatching {
        val request = Request.Builder()
            .url("${controlUrl.trimEnd('/')}$path")
            .header("X-API-Key", apiKey)
            .put(body.toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    private companion object {
        val JSON = "application/json".toMediaType()
    }
}
