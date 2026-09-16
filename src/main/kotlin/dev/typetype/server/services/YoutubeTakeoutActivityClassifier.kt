package dev.typetype.server.services

object YoutubeTakeoutActivityClassifier {
    private val watchedPhrases = setOf(
        "You watched",
        "Vous avez regardé",
        "Has visto",
        "Has visto el vídeo",
        "Ha visto",
        "Você assistiu",
        "Você assistiu a",
        "Você assistiu ao vídeo",
        "hai guardato",
        "Hai guardato",
        "Hai guardato il video",
        "İzlediniz",
        "du hast angesehen",
        "Du hast dir angesehen",
        "Du hast dir das angesehen",
        "je hebt gekeken",
        "je hebt gekeken naar",
        "obejrzałeś",
        "obejrzałaś",
        "obejrzano",
        "вы смотрели",
        "вы посмотрели",
        "ви дивилися",
        "ви переглянули",
        "du tittade på",
        "du har sett",
        "du så på",
        "katsoit",
        "sledovali jste",
        "ai vizionat",
        "anda menonton",
        "bạn đã xem",
        "आपने देखा",
        "あなたが視聴した動画",
        "視聴した動画",
        "視聴しました",
        "動画を視聴しました",
        "시청한 동영상",
        "동영상을 시청했습니다",
        "已观看",
        "已觀看",
        "观看了",
        "觀看了",
        "شاهدت",
        "لقد شاهدت",
    )

    private val likedPhrases = setOf(
        "You liked",
        "Liked",
        "Vous avez aimé",
        "A aimé",
        "te ha gustado",
        "te gustó",
        "ha gustado",
        "Você gostou",
        "Você curtiu",
        "Você marcou como gostei",
        "hai messo mi piace",
        "ti è piaciuto",
        "Beğendiniz",
        "gefällt mir",
        "dir gefällt",
        "je hebt dit leuk gevonden",
        "podobał ci się",
        "понравилось",
        "вы поставили отметку нравится",
        "сподобалося",
        "du gillade",
        "du kunne godt lide",
        "du likte",
        "pidit",
        "líbilo se vám",
        "ți-a plăcut",
        "anda menyukai",
        "bạn đã thích",
        "आपको पसंद आया",
        "高く評価しました",
        "좋아요 표시함",
        "点赞了",
        "按讚",
        "您喜欢了",
        "أعجبك",
    )

    private val subscribedPhrases = setOf(
        "You subscribed to",
        "Vous vous êtes abonné à",
        "te has suscrito a",
        "Você se inscreveu em",
        "Você se inscreveu no canal",
        "ti sei iscritto a",
        "ti sei iscritta a",
        "Abone oldunuz",
        "du hast abonniert",
        "du hast den kanal abonniert",
        "je hebt je geabonneerd op",
        "zasubskrybowałeś",
        "zasubskrybowałaś",
        "вы подписались на",
        "вы подписались на канал",
        "ви підписалися на",
        "du prenumererade på",
        "du abonnerede på",
        "du abonnerte på",
        "tilasit",
        "te-ai abonat la",
        "anda berlangganan",
        "bạn đã đăng ký",
        "आपने सदस्यता ली",
        "登録しました",
        "チャンネル登録しました",
        "구독함",
        "구독했습니다",
        "已订阅",
        "已訂閱",
        "订阅了",
        "訂閱了",
        "اشتركت في",
        "لقد اشتركت في",
    )
    private val normalizedWatchedPhrases = watchedPhrases.mapTo(hashSetOf(), YoutubeTakeoutTextNormalizer::normalize)
    private val normalizedLikedPhrases = likedPhrases.mapTo(hashSetOf(), YoutubeTakeoutTextNormalizer::normalize)
    private val normalizedSubscribedPhrases = subscribedPhrases.mapTo(hashSetOf(), YoutubeTakeoutTextNormalizer::normalize)

    fun isWatched(value: String): Boolean = containsAny(value, normalizedWatchedPhrases)

    fun isLiked(value: String): Boolean = containsAny(value, normalizedLikedPhrases)

    fun isSubscribed(value: String): Boolean = containsAny(value, normalizedSubscribedPhrases)

    val watchedPattern: String = pattern(watchedPhrases)

    val likedPattern: String = pattern(likedPhrases)

    val subscribedPattern: String = pattern(subscribedPhrases)

    private fun containsAny(value: String, phrases: Set<String>): Boolean {
        val normalized = YoutubeTakeoutTextNormalizer.normalize(value)
        return phrases.any { YoutubeTakeoutTextNormalizer.normalize(it) in normalized }
    }

    private fun pattern(phrases: Set<String>): String = phrases.joinToString("|") { Regex.escape(it) }
}
