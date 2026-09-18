package dev.typetype.server.services

import java.security.SecureRandom

private const val YOUTUBE_SESSION_CODE_LENGTH = 8
private const val YOUTUBE_SESSION_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private val youtubeSessionCodeRandom = SecureRandom()

fun newYoutubeSessionCode(): String = buildString(YOUTUBE_SESSION_CODE_LENGTH) {
    repeat(YOUTUBE_SESSION_CODE_LENGTH) {
        append(YOUTUBE_SESSION_CODE_ALPHABET[youtubeSessionCodeRandom.nextInt(YOUTUBE_SESSION_CODE_ALPHABET.length)])
    }
}
