package dev.typetype.server.services

enum class FocusAllowOnceKind(
    val wireName: String,
    val dailyLimit: Int,
) {
    OUTSIDE_GROUP("outside_group", 3),
    ENTERTAINMENT_REWATCH("entertainment_rewatch", 2),
    ;

    companion object {
        fun fromWire(raw: String?): FocusAllowOnceKind? {
            val normalized = raw?.trim()?.lowercase()
            if (normalized.isNullOrEmpty()) return OUTSIDE_GROUP
            return entries.firstOrNull { it.wireName == normalized }
        }
    }
}
