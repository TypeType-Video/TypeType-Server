package dev.typetype.server.services

import dev.typetype.server.cache.CacheJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object HomeRecommendationCursorCodec {
    private const val MULTI_PATTERN = """\{"s":(\d+),"d":(\d+)}"""
    private const val LEGACY_PATTERN = """\{"index":(\d+)}"""

    fun decode(rawCursor: String?): HomeRecommendationCursor? {
        if (rawCursor.isNullOrBlank()) {
            return HomeRecommendationCursor()
        }
        val decoded = decodePayload(rawCursor) ?: return null
        val full = runCatching {
            CacheJson.decodeFromString<HomeRecommendationCursorPayload>(decoded)
        }.getOrNull()
        if (full != null) {
            return HomeRecommendationCursor(
                subscriptionIndex = full.s,
                discoveryIndex = full.d,
                subscriptionRun = full.r,
                preferDiscovery = full.p == 1,
                rotationSeed = full.f,
                recentChannels = full.c,
                recentSemanticKeys = full.k,
                creatorMomentum = full.m,
                creatorCooldownUntilMs = full.o,
                recentTopicPairs = full.t,
                recentUrls = full.u,
                personaState = HomeRecommendationPersonaState(
                    persona = when (full.q) {
                        1 -> HomeRecommendationSessionPersona.QUICK
                        2 -> HomeRecommendationSessionPersona.DEEP
                        else -> HomeRecommendationSessionPersona.AUTO
                    },
                    quickEvidence = full.qe,
                    deepEvidence = full.de,
                ),
            )
        }
        val multi = Regex(MULTI_PATTERN).matchEntire(decoded)
        if (multi != null) {
            val subscriptionIndex = multi.groupValues[1].toIntOrNull() ?: return null
            val discoveryIndex = multi.groupValues[2].toIntOrNull() ?: return null
            return HomeRecommendationCursor(
                subscriptionIndex = subscriptionIndex,
                discoveryIndex = discoveryIndex,
                subscriptionRun = 0,
                preferDiscovery = true,
            )
        }
        val legacy = Regex(LEGACY_PATTERN).matchEntire(decoded)
        if (legacy != null) {
            val index = legacy.groupValues[1].toIntOrNull() ?: return null
            return HomeRecommendationCursor(
                subscriptionIndex = index,
                discoveryIndex = index,
                subscriptionRun = 0,
                preferDiscovery = true,
            )
        }
        return null
    }

    fun encode(cursor: HomeRecommendationCursor): String {
        val payload = CacheJson.encodeToString(
            HomeRecommendationCursorPayload(
                s = cursor.subscriptionIndex,
                d = cursor.discoveryIndex,
                r = cursor.subscriptionRun,
                p = if (cursor.preferDiscovery) 1 else 0,
                f = cursor.rotationSeed,
                c = cursor.recentChannels,
                k = cursor.recentSemanticKeys,
                m = cursor.creatorMomentum,
                o = cursor.creatorCooldownUntilMs,
                t = cursor.recentTopicPairs,
                u = cursor.recentUrls,
                q = when (cursor.personaState.persona) {
                    HomeRecommendationSessionPersona.AUTO -> 0
                    HomeRecommendationSessionPersona.QUICK -> 1
                    HomeRecommendationSessionPersona.DEEP -> 2
                },
                qe = cursor.personaState.quickEvidence,
                de = cursor.personaState.deepEvidence,
            )
        )
        val compressed = ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            output.toByteArray()
        }
        return COMPRESSED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    private fun decodePayload(rawCursor: String): String? {
        val compressed = rawCursor.startsWith(COMPRESSED_PREFIX)
        val encoded = if (compressed) rawCursor.removePrefix(COMPRESSED_PREFIX) else rawCursor
        val bytes = runCatching { Base64.getUrlDecoder().decode(encoded) }.getOrNull() ?: return null
        if (!compressed) return bytes.toString(Charsets.UTF_8)
        return runCatching {
            InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
                val decoded = input.readNBytes(MAX_DECODED_BYTES + 1)
                require(decoded.size <= MAX_DECODED_BYTES)
                decoded.toString(Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private const val COMPRESSED_PREFIX = "z."
    private const val MAX_DECODED_BYTES = 32 * 1024
}
