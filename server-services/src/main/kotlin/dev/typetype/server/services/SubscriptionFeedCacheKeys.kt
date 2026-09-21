package dev.typetype.server.services

import java.security.MessageDigest

object SubscriptionFeedCacheKeys {
    fun feed(userId: String): String = "feed:${hash(userId)}"

    fun previousFeed(userId: String): String = "feed:previous:${hash(userId)}"

    fun invalidation(userId: String): String = "feed:invalidation:${hash(userId)}"

    fun selection(userId: String, slot: Int): String = "feed:selection:${hash(userId)}:$slot"

    fun shorts(userId: String): String = "feed:shorts:${hash(userId)}"

    private fun hash(userId: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(userId.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
