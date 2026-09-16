package dev.typetype.server.services

object YoutubeTakeoutSchemaHints {
    private val channelIdRegex = Regex("""^(UC|HC)[A-Za-z0-9_-]{10,}$""")
    private val videoIdRegex = Regex("""^[A-Za-z0-9_-]{6,}$""")
    private val playlistIdRegex = Regex("""^(PL|UU|LL|RD|OLAK5uy_)[A-Za-z0-9_-]{6,}$""")
    private val channelUrlRegex = Regex("""youtube\.com/(channel/|@|c/|user/)""", RegexOption.IGNORE_CASE)
    private val titleWords = YoutubeTakeoutSchemaTerms.titleWords
    private val playlistWords = YoutubeTakeoutSchemaTerms.playlistWords
    private val channelWords = YoutubeTakeoutSchemaTerms.channelWords
    private val videoWords = YoutubeTakeoutSchemaTerms.videoWords
    fun isChannelIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "channel id" ||
            normalized == "id des chaines" ||
            normalized == "id de la chaine" ||
            (channelWords.any { normalize(it) in normalized } && hasIdentifier(normalized))
    }

    fun isChannelUrlHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "channel url" ||
            normalized == "url des chaines" ||
            normalized == "url de la chaine" ||
            ("url" in words(normalized) && channelWords.any { normalize(it) in normalized })
    }

    fun isChannelTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "titres des chaines" ||
            normalized == "titre de la chaine" ||
            titleWords.any { normalize(it) in normalized } &&
            (channelWords.any { normalize(it) in normalized } || titleWords.any { normalize(it) == normalized })
    }

    fun isPlaylistIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "playlist id" ||
            normalized == "id de la playlist" ||
            (isPlaylistText(normalized) && hasIdentifier(normalized))
    }

    fun isPlaylistTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return isPlaylistText(normalized) && titleWords.any { normalize(it) in normalized }
    }

    fun isVideoIdHeader(value: String): Boolean {
        val normalized = normalize(value)
        return normalized == "video id" || normalized == "id video" ||
            (videoWords.any { normalize(it) in normalized } && hasIdentifier(normalized))
    }

    fun isVideoTitleHeader(value: String): Boolean {
        val normalized = normalize(value)
        return videoWords.any { normalize(it) in normalized } && titleWords.any { normalize(it) in normalized }
    }

    fun isPlaylistItemAddedAtHeader(value: String): Boolean {
        val normalized = normalize(value)
        val hasTemporalWord = TEMPORAL_WORDS.any { normalize(it) in normalized }
        return hasTemporalWord && (
            isPlaylistText(normalized) || videoWords.any { normalize(it) in normalized } ||
                "added at" in normalized || "date added" in normalized || "created at" in normalized ||
                normalized == "timestamp"
        )
    }

    fun isUrlHeader(value: String): Boolean = "url" in words(normalize(value))

    fun looksLikeChannelId(value: String): Boolean = channelIdRegex.matches(value.trim())

    fun looksLikeVideoId(value: String): Boolean {
        val trimmed = value.trim()
        return videoIdRegex.matches(trimmed) && !looksLikeChannelId(trimmed) && !looksLikePlaylistId(trimmed)
    }

    fun looksLikePlaylistId(value: String): Boolean = playlistIdRegex.matches(value.trim())

    fun containsChannelUrl(value: String): Boolean = channelUrlRegex.containsMatchIn(value)

    fun containsWatchUrl(value: String): Boolean {
        val normalized = value.lowercase()
        return "youtube.com/watch?v=" in normalized || "youtube.com/shorts/" in normalized || "youtu.be/" in normalized
    }

    fun normalize(value: String): String = YoutubeTakeoutTextNormalizer.normalize(value)

    fun isPlaylistText(value: String): Boolean {
        val normalized = normalize(value)
        return playlistWords.any { normalize(it) in normalized }
    }

    fun isPlaylistManifestName(value: String): Boolean =
        YoutubeTakeoutSchemaTerms.playlistManifestNames.contains(normalize(value))

    fun isSubscriptionText(value: String): Boolean =
        YoutubeTakeoutSchemaTerms.subscriptionWords.any { normalize(it) in normalize(value) }

    private fun hasIdentifier(normalized: String): Boolean {
        val parts = words(normalized)
        return "id" in parts || "identifier" in parts || "identificador" in parts ||
            "identifiant" in parts || "kimligi" in parts || "kimliği" in parts ||
            "идентификатор" in normalized || "identyfikator" in parts || "identifikator" in parts ||
            "kennung" in parts || "識別子" in normalized || "標識符" in normalized ||
            "标识符" in normalized || "معرف" in normalized
    }

    private fun words(value: String): Set<String> = value.split(' ').filter { it.isNotBlank() }.toSet()

    private val TEMPORAL_WORDS = setOf(
        "added", "date", "time", "timestamp", "creation", "created", "fecha", "marca de tiempo", "creacion",
        "ajoute", "datum", "zeit", "erstellt", "data", "ora", "creazione", "criacao", "eklenme", "olusturma",
        "дата", "время", "создания", "добавления", "日時", "时间", "日期", "创建", "추가", "생성", "التاريخ", "وقت",
        "إضافة", "बनाने", "समय", "ημερομηνία", "เวลา",
    )

}
