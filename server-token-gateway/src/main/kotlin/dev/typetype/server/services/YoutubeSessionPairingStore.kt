package dev.typetype.server.services

import dev.typetype.server.db.DatabaseFactory
import dev.typetype.server.db.tables.YoutubeSessionPairingsTable
import dev.typetype.server.models.YoutubeSessionPairingResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

private const val YOUTUBE_SESSION_PAIRING_TTL_MS = 5 * 60 * 1000L

class YoutubeSessionPairingStore(
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val codeGenerator: () -> String = ::newYoutubeSessionCode,
) {
    suspend fun create(userId: String): YoutubeSessionPairingResponse {
        val now = nowMillis()
        val expiresAt = now + YOUTUBE_SESSION_PAIRING_TTL_MS
        val code = DatabaseFactory.query {
            YoutubeSessionPairingsTable.deleteWhere { YoutubeSessionPairingsTable.expiresAt lessEq now }
            val pairingCode = uniqueCode()
            YoutubeSessionPairingsTable.insert {
                it[YoutubeSessionPairingsTable.code] = pairingCode
                it[YoutubeSessionPairingsTable.userId] = userId
                it[createdAt] = now
                it[YoutubeSessionPairingsTable.expiresAt] = expiresAt
            }
            pairingCode
        }
        return YoutubeSessionPairingResponse(code = code, expiresAt = expiresAt)
    }

    private fun uniqueCode(): String {
        repeat(10) {
            val code = codeGenerator().trim().uppercase()
            if (YoutubeSessionPairingsTable.selectAll().where { YoutubeSessionPairingsTable.code eq code }.empty()) {
                return code
            }
        }
        error("Unable to allocate YouTube session pairing code")
    }
}
