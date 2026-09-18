package dev.typetype.server.services

object YoutubeTakeoutPathHints {
    fun isYoutubeHtml(path: String): Boolean =
        path.endsWith(".html", ignoreCase = true) && "youtube" in path.lowercase()

    fun isHistoryEntry(path: String): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(path)
        return NORMALIZED_HISTORY_MARKERS.any { it in normalized }
    }

    private val HISTORY_MARKERS = setOf(
        "watch history",
        "historique",
        "historique des videos regardees",
        "historico",
        "historico de visualizacao",
        "historico de exibicao",
        "historial",
        "historial de reproduccion",
        "historial de visualizacion",
        "cronologia",
        "cronologia de reproduccion",
        "verlauf",
        "wiedergabeverlauf",
        "kijkgeschiedenis",
        "historia ogladania",
        "izleme gecmisi",
        "lich su xem",
        "ประวัติการดู",
        "ประวัติการรับชม",
        "история просмотров",
        "история просмотра",
        "історія перегляду",
        "視聴履歴",
        "再生履歴",
        "시청 기록",
        "观看记录",
        "觀看記錄",
        "سجل المشاهدة",
        "تاریخچه تماشا",
        "देखने का इतिहास",
    )

    private val NORMALIZED_HISTORY_MARKERS = HISTORY_MARKERS.map(YoutubeTakeoutTextNormalizer::normalize)
}
