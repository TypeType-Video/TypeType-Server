package dev.typetype.server.services

import dev.typetype.server.models.HomeRecommendationPool
import dev.typetype.server.models.VideoItem
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

object HomeRecommendationPoolRotation {
    private const val ROTATION_WINDOW_SIZE = 40
    private const val SUBSCRIPTION_SALT = 0x5EED1234L
    private const val DISCOVERY_SALT = 0xD15C0A7L

    fun newSeed(): Long {
        var seed = ThreadLocalRandom.current().nextLong()
        while (seed == 0L) seed = ThreadLocalRandom.current().nextLong()
        return seed
    }

    fun apply(pool: HomeRecommendationPool, seed: Long): HomeRecommendationPool = pool.copy(
        subscriptions = rotate(pool.subscriptions, seed xor SUBSCRIPTION_SALT),
        discovery = rotate(pool.discovery, seed xor DISCOVERY_SALT),
    )

    private fun rotate(source: List<VideoItem>, seed: Long): List<VideoItem> {
        if (source.size <= 1) return source
        val windowSize = minOf(source.size, ROTATION_WINDOW_SIZE)
        if (windowSize <= 1) return source
        val window = source.take(windowSize).toMutableList()
        val random = Random(seed)
        for (index in window.lastIndex downTo 1) {
            val swapIndex = random.nextInt(index + 1)
            val item = window[index]
            window[index] = window[swapIndex]
            window[swapIndex] = item
        }
        return window + source.drop(windowSize)
    }
}
