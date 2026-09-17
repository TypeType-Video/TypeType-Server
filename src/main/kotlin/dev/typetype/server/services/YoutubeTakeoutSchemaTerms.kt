package dev.typetype.server.services

internal object YoutubeTakeoutSchemaTerms {
    val titleWords = setOf(
        "title", "titles", "titre", "titres", "titulo", "titulos", "baslik", "baslık", "başlık",
        "basligi", "baslıgı", "başlığı", "adi", "adı", "nombre", "nome", "nom", "name", "names",
        "titel", "titolo", "titulo original", "tytul", "название", "наименование", "название видео",
        "動画タイトル", "動画のタイトル", "제목", "视频标题", "影片標題", "naam", "عنوان", "शीर्षक", "τίτλος",
    )

    val playlistWords = setOf(
        "playlist", "playlists", "oynatma listesi", "oynatma listeleri", "playlistler",
        "lista de reproduccion", "listas de reproduccion", "liste de lecture", "listes de lecture",
        "lista di riproduzione", "liste di riproduzione", "lista de reproducao", "listas de reproducao",
        "wiedergabeliste", "wiedergabelisten", "afspeellijst", "afspeellijsten",
        "список воспроизведения", "списки воспроизведения", "плейлист", "плейлисты", "再生リスト",
        "재생목록", "播放列表", "播放清單", "قائمة التشغيل", "قوائم التشغيل", "प्लेलिस्ट", "प्लेलिस्टें",
        "λίστα αναπαραγωγής", "λίστες αναπαραγωγής", "danh sach phat", "รายการเล่น", "เพลย์ลิสต์",
        "רשימת השמעה", "רשימות השמעה",
    )

    val channelWords = setOf(
        "channel", "chaine", "canal", "canale", "kanal", "kanaal", "канал", "канали", "kanał",
        "チャンネル", "채널", "频道", "頻道", "قناة", "चैनल", "κανάλι", "kenh", "ช่อง", "ערוץ",
    )

    val videoWords = setOf(
        "video", "videos", "vidéo", "vidéos", "vídeo", "vídeos", "動画", "동영상", "비디오", "видео",
        "视频", "影片", "film", "filmy", "فيديو", "فيديوهات", "वीडियो", "βίντεο", "วิดีโอ", "ভিডিও", "סרטון",
    )

    val subscriptionWords = setOf(
        "subscriptions", "suscripciones", "inscricoes", "inscrições", "iscrizioni", "abbonamenti", "abos",
        "abonelikler", "abonnements", "abonnementen", "abonnierte kanäle", "subskrypcje", "подписки",
        "підписки", "登録チャンネル", "チャンネル登録", "구독", "订阅", "訂閱", "الاشتراكات",
        "การสมัครรับข้อมูล", "การติดตาม", "מינויים", "সাবস্ক্রিপশন", "kenh da dang ky",
    )

    val playlistManifestNames = setOf(
        "playlists", "oynatma listeleri", "oynatma listesi", "listas de reproduccion", "listes de lecture",
        "liste di riproduzione", "listas de reproducao", "wiedergabelisten", "wiedergabeliste",
        "afspeellijsten", "afspeellijst", "списки воспроизведения", "список воспроизведения", "再生リスト",
        "재생목록", "播放列表", "播放清單", "قوائم التشغيل", "قائمة التشغيل", "danh sach phat",
        "รายการเล่น", "เพลย์ลิสต์", "רשימת השמעה", "רשימות השמעה",
    ).mapTo(mutableSetOf(), YoutubeTakeoutTextNormalizer::normalize)
}
