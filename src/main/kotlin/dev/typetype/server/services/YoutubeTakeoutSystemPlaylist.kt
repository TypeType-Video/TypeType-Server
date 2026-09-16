package dev.typetype.server.services

object YoutubeTakeoutSystemPlaylist {
    private val watchLaterPhrases = setOf(
        "watch later",
        "a regarder plus tard",
        "regarder plus tard",
        "ver mas tarde",
        "assistir mais tarde",
        "ver mais tarde",
        "guarda piu tardi",
        "daha sonra izle",
        "daha sonra izlenecekler",
        "spater ansehen",
        "später ansehen",
        "later bekijken",
        "obejrzyj pozniej",
        "посмотреть позже",
        "смотреть позже",
        "後で見る",
        "나중에 볼 동영상",
        "稍后观看",
        "稍後觀看",
        "بعدا تماشا کنید",
        "مشاهدة لاحقا",
        "المشاهدة لاحقا",
        "переглянути пізніше",
        "बाद में देखें",
        "ดูภายหลัง",
        "παρακολούθηση αργότερα",
    )

    private val likedPhrases = setOf(
        "liked videos",
        "videos liked",
        "videos que j aime",
        "j aime",
        "videos que me gustan",
        "videos que gostei",
        "videos curtidos",
        "videos de que gostei",
        "videos que eu gostei",
        "video piaciuti",
        "video che mi piacciono",
        "begendigim videolar",
        "begendiğim videolar",
        "beğendigim videolar",
        "beğendiğim videolar",
        "begenilen videolar",
        "beğenilen videolar",
        "mit gefallt",
        "gefällt mir",
        "videos die mir gefallen",
        "leuke videos",
        "polubione filmy",
        "понравившиеся видео",
        "понравились видео",
        "高く評価した動画",
        "좋아요 표시한 동영상",
        "喜欢的视频",
        "喜歡的影片",
        "الفيديوهات التي أعجبتني",
        "वीडियो जो मुझे पसंद हैं",
        "βίντεο που μου αρέσουν",
        "улюблені відео",
    )

    fun canonicalKey(value: String): String? {
        val normalized = normalize(value)
        if (watchLaterPhrases.any { normalize(it) in normalized }) return WATCH_LATER
        if (likedPhrases.any { normalize(it) in normalized }) return LIKED_VIDEOS
        return null
    }

    fun isWatchLater(value: String): Boolean = canonicalKey(value) == WATCH_LATER

    fun isLiked(value: String): Boolean = canonicalKey(value) == LIKED_VIDEOS

    private fun normalize(value: String): String = YoutubeTakeoutTextNormalizer.normalize(value)

    const val WATCH_LATER: String = "Watch later"
    const val LIKED_VIDEOS: String = "Liked videos"
}
