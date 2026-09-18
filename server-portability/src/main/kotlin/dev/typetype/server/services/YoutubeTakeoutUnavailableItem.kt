package dev.typetype.server.services

object YoutubeTakeoutUnavailableItem {
    fun matches(title: String): Boolean =
        YoutubeTakeoutTextNormalizer.normalize(title) in TITLES

    private val TITLES = setOf(
        "deleted video",
        "private video",
        "video deleted",
        "video indisponible",
        "video no disponible",
        "video privee",
        "video privado",
        "video supprimee",
        "video unavailable",
    )
}
