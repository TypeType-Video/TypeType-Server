package dev.typetype.server.services

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

object SubscriptionMutationLock {
    fun acquire(userId: String) {
        val userKey = userId.hashCode() and Int.MAX_VALUE
        TransactionManager.current().exec(
            "SELECT pg_advisory_xact_lock($LOCK_NAMESPACE, $userKey)",
        )
    }

    fun acquireRead(userId: String) {
        val userKey = userId.hashCode() and Int.MAX_VALUE
        TransactionManager.current().exec(
            "SELECT pg_advisory_xact_lock_shared($LOCK_NAMESPACE, $userKey)",
        )
    }

    // Precomputed PostgreSQL hashtext('subscriptions').
    private const val LOCK_NAMESPACE = 720_815_616
}
