package dev.typetype.server.services

import dev.typetype.server.db.tables.FocusAiReviewsTable
import dev.typetype.server.db.tables.FocusAllowOnceGrantsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID

internal object FocusAllowOnceQueries {
    const val SOURCE_CAPTCHA = "captcha"
    const val SOURCE_AI_APPROVED = "ai_approved"

    fun activeVideoKeysInTx(userId: String, now: Long): Set<String> =
        FocusAllowOnceGrantsTable.select(FocusAllowOnceGrantsTable.videoKey)
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.unlockAt lessEq now) and
                    (FocusAllowOnceGrantsTable.expiresAt greater now)
            }
            .mapTo(HashSet()) { it[FocusAllowOnceGrantsTable.videoKey] }

    fun pendingGrantInTx(userId: String, videoKey: String, now: Long): FocusAllowOnceGrantMatch? =
        FocusAllowOnceGrantsTable
            .select(
                FocusAllowOnceGrantsTable.grantKind,
                FocusAllowOnceGrantsTable.unlockAt,
                FocusAllowOnceGrantsTable.expiresAt,
            )
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.videoKey eq videoKey) and
                    (FocusAllowOnceGrantsTable.grantSource eq SOURCE_CAPTCHA) and
                    (FocusAllowOnceGrantsTable.unlockAt greater now) and
                    (FocusAllowOnceGrantsTable.expiresAt greater now)
            }
            .orderBy(FocusAllowOnceGrantsTable.unlockAt to SortOrder.DESC)
            .firstOrNull()
            ?.let {
                FocusAllowOnceGrantMatch(
                    kind = FocusAllowOnceKind.fromWire(it[FocusAllowOnceGrantsTable.grantKind])
                        ?: FocusAllowOnceKind.OUTSIDE_GROUP,
                    unlockAt = it[FocusAllowOnceGrantsTable.unlockAt],
                    expiresAt = it[FocusAllowOnceGrantsTable.expiresAt],
                )
            }

    fun activeOrPendingGrantInTx(
        userId: String,
        videoKey: String,
        kind: FocusAllowOnceKind,
        now: Long,
    ): FocusAllowOnceGrantMatch? =
        FocusAllowOnceGrantsTable
            .select(
                FocusAllowOnceGrantsTable.grantKind,
                FocusAllowOnceGrantsTable.unlockAt,
                FocusAllowOnceGrantsTable.expiresAt,
            )
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.videoKey eq videoKey) and
                    (FocusAllowOnceGrantsTable.grantKind eq kind.wireName) and
                    (FocusAllowOnceGrantsTable.expiresAt greater now)
            }
            .orderBy(FocusAllowOnceGrantsTable.expiresAt to SortOrder.DESC)
            .firstOrNull()
            ?.let {
                FocusAllowOnceGrantMatch(
                    kind = kind,
                    unlockAt = it[FocusAllowOnceGrantsTable.unlockAt],
                    expiresAt = it[FocusAllowOnceGrantsTable.expiresAt],
                )
            }

    fun countUsedInDayTx(userId: String, dayKey: String, kind: FocusAllowOnceKind): Int =
        FocusAllowOnceGrantsTable.selectAll()
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.dayKey eq dayKey) and
                    (FocusAllowOnceGrantsTable.grantKind eq kind.wireName) and
                    (FocusAllowOnceGrantsTable.grantSource eq SOURCE_CAPTCHA)
            }
            .count()
            .toInt()

    fun insertGrantInTx(
        userId: String,
        videoKey: String,
        dayKey: String,
        kind: FocusAllowOnceKind,
        source: String,
        grantedAt: Long,
        unlockAt: Long,
        expiresAt: Long,
    ): Unit {
        FocusAllowOnceGrantsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[FocusAllowOnceGrantsTable.userId] = userId
            it[FocusAllowOnceGrantsTable.videoKey] = videoKey
            it[FocusAllowOnceGrantsTable.dayKey] = dayKey
            it[FocusAllowOnceGrantsTable.grantKind] = kind.wireName
            it[FocusAllowOnceGrantsTable.grantSource] = source
            it[FocusAllowOnceGrantsTable.grantedAt] = grantedAt
            it[FocusAllowOnceGrantsTable.unlockAt] = unlockAt
            it[FocusAllowOnceGrantsTable.expiresAt] = expiresAt
        }
    }

    fun deletePendingCaptchaGrantsInTx(userId: String, videoKey: String, now: Long): FocusAllowOnceKind? {
        val pendingKind = FocusAllowOnceGrantsTable.select(FocusAllowOnceGrantsTable.grantKind)
            .where {
                (FocusAllowOnceGrantsTable.userId eq userId) and
                    (FocusAllowOnceGrantsTable.videoKey eq videoKey) and
                    (FocusAllowOnceGrantsTable.grantSource eq SOURCE_CAPTCHA) and
                    (FocusAllowOnceGrantsTable.unlockAt greater now)
            }
            .firstOrNull()
            ?.let { FocusAllowOnceKind.fromWire(it[FocusAllowOnceGrantsTable.grantKind]) }
            ?: return null

        val deleted = FocusAllowOnceGrantsTable.deleteWhere {
            (FocusAllowOnceGrantsTable.userId eq userId) and
                (FocusAllowOnceGrantsTable.videoKey eq videoKey) and
                (FocusAllowOnceGrantsTable.grantSource eq SOURCE_CAPTCHA) and
                (FocusAllowOnceGrantsTable.unlockAt greater now)
        }
        return if (deleted > 0) pendingKind else null
    }

    fun activeRejectionInTx(userId: String, videoKey: String, now: Long): FocusAiRejectionRecord? =
        FocusAiReviewsTable
            .select(
                FocusAiReviewsTable.kind,
                FocusAiReviewsTable.score,
                FocusAiReviewsTable.cooldownMs,
                FocusAiReviewsTable.rejectionExpiresAt,
            )
            .where {
                (FocusAiReviewsTable.userId eq userId) and
                    (FocusAiReviewsTable.videoKey eq videoKey) and
                    (FocusAiReviewsTable.rejectionExpiresAt greater now)
            }
            .orderBy(FocusAiReviewsTable.reviewedAt to SortOrder.DESC)
            .firstOrNull()
            ?.let {
                FocusAiRejectionRecord(
                    kind = FocusAllowOnceKind.fromWire(it[FocusAiReviewsTable.kind])
                        ?: FocusAllowOnceKind.OUTSIDE_GROUP,
                    score = it[FocusAiReviewsTable.score],
                    cooldownMs = it[FocusAiReviewsTable.cooldownMs],
                    rejectionExpiresAt = it[FocusAiReviewsTable.rejectionExpiresAt],
                )
            }

    fun insertAiRejectionInTx(
        userId: String,
        videoKey: String,
        kind: FocusAllowOnceKind,
        score: Int,
        cooldownMs: Long,
        reviewedAt: Long,
        rejectionExpiresAt: Long,
    ): Unit {
        FocusAiReviewsTable.insert {
            it[id] = UUID.randomUUID().toString()
            it[FocusAiReviewsTable.userId] = userId
            it[FocusAiReviewsTable.videoKey] = videoKey
            it[FocusAiReviewsTable.kind] = kind.wireName
            it[FocusAiReviewsTable.score] = score
            it[FocusAiReviewsTable.cooldownMs] = cooldownMs
            it[FocusAiReviewsTable.reviewedAt] = reviewedAt
            it[FocusAiReviewsTable.rejectionExpiresAt] = rejectionExpiresAt
        }
    }
}
