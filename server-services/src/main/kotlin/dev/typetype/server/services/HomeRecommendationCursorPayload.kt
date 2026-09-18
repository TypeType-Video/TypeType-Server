package dev.typetype.server.services

import kotlinx.serialization.Serializable

@Serializable
data class HomeRecommendationCursorPayload(
    val s: Int,
    val d: Int,
    val r: Int,
    val p: Int,
    val f: Long = 0L,
    val c: List<String> = emptyList(),
    val k: List<String> = emptyList(),
    val m: Map<String, Int> = emptyMap(),
    val o: Map<String, Long> = emptyMap(),
    val t: List<String> = emptyList(),
    val u: List<String> = emptyList(),
    val q: Int = 0,
    val qe: Int = 0,
    val de: Int = 0,
)
