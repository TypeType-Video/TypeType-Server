package dev.typetype.server.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class BiliBiliQrLoginService(
    private val client: OkHttpClient = OkHttpClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generate(): BiliBiliQrGenerateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(GENERATE_URL)
            .header("User-Agent", WEB_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        val response = client.newCall(request).execute()
        response.use {
            val body = it.body.string() ?: return@withContext BiliBiliQrGenerateResult.Error("Empty response")
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext BiliBiliQrGenerateResult.Error("Invalid JSON")
            val data = root["data"]?.jsonObject
                ?: return@withContext BiliBiliQrGenerateResult.Error("Missing data")
            val url = data["url"]?.jsonPrimitive?.content
                ?: return@withContext BiliBiliQrGenerateResult.Error("Missing QR URL")
            val key = data["qrcode_key"]?.jsonPrimitive?.content
                ?: return@withContext BiliBiliQrGenerateResult.Error("Missing QR key")
            BiliBiliQrGenerateResult.Success(
                qrUrl = url,
                qrcodeKey = key,
                expiresAt = nowMillis() + QR_TTL_MS,
            )
        }
    }

    suspend fun poll(qrcodeKey: String): BiliBiliQrPollResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$POLL_URL?qrcode_key=$qrcodeKey")
            .header("User-Agent", WEB_USER_AGENT)
            .header("Referer", "https://www.bilibili.com/")
            .build()
        val response = client.newCall(request).execute()
        response.use { httpResponse ->
            val body = httpResponse.body?.string() ?: return@withContext BiliBiliQrPollResult.Error("Empty response")
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return@withContext BiliBiliQrPollResult.Error("Invalid JSON")
            val data = root["data"]?.jsonObject
                ?: return@withContext BiliBiliQrPollResult.Error("Missing data")
            val code = data["code"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: return@withContext BiliBiliQrPollResult.Error("Missing code")
            when (code) {
                0 -> {
                    val cookies = httpResponse.headers("Set-Cookie")
                        .filter { it.contains("SESSDATA=") || it.contains("bili_jct=") || it.contains("buvid3=") }
                        .mapNotNull { cookie ->
                            val parts = cookie.split(";").firstOrNull()?.trim()
                            parts?.takeIf { it.isNotEmpty() }
                        }
                    val cookieHeader = cookies.joinToString("; ")
                    if (cookieHeader.contains("SESSDATA=")) {
                        BiliBiliQrPollResult.Confirmed(cookieHeader)
                    } else {
                        BiliBiliQrPollResult.Error("Missing SESSDATA in response")
                    }
                }
                86038 -> BiliBiliQrPollResult.Expired
                86090 -> BiliBiliQrPollResult.Scanned
                86001 -> BiliBiliQrPollResult.Waiting
                else -> BiliBiliQrPollResult.Error("Unexpected code: $code")
            }
        }
    }

    companion object {
        private const val GENERATE_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
        private const val POLL_URL = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
        private const val QR_TTL_MS = 180_000L
        private const val WEB_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }
}

sealed class BiliBiliQrGenerateResult {
    data class Success(val qrUrl: String, val qrcodeKey: String, val expiresAt: Long) : BiliBiliQrGenerateResult()
    data class Error(val message: String) : BiliBiliQrGenerateResult()
}

sealed class BiliBiliQrPollResult {
    data class Confirmed(val cookieHeader: String) : BiliBiliQrPollResult()
    object Scanned : BiliBiliQrPollResult()
    object Waiting : BiliBiliQrPollResult()
    object Expired : BiliBiliQrPollResult()
    data class Error(val message: String) : BiliBiliQrPollResult()
}
