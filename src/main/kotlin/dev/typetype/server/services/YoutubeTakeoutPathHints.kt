package dev.typetype.server.services

object YoutubeTakeoutPathHints {
    fun isYoutubeHtml(path: String): Boolean =
        path.endsWith(".html", ignoreCase = true) && "youtube" in path.lowercase()

    fun isHistoryEntry(path: String): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(path)
        return HISTORY_MARKERS.any { it in normalized }
    }

    private val HISTORY_MARKERS = setOf(
        "watch history",
        "historique",
        "historico",
        "historial",
        "cronologia",
        "verlauf",
        "kijkgeschiedenis",
        "historia ogladania",
        "izleme gecmisi",
        "история просмотров",
        "история просмотра",
        "історія перегляду",
        "視聴履歴",
        "시청 기록",
        "观看记录",
        "觀看記錄",
        "سجل المشاهدة",
        "تاریخچه تماشا",
        "देखने का इतिहास",
    )
}
