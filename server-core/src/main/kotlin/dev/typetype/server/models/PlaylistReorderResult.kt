package dev.typetype.server.models

sealed class PlaylistReorderResult {
    data object Success : PlaylistReorderResult()
    data object NotFound : PlaylistReorderResult()
    data class InvalidOrder(val message: String) : PlaylistReorderResult()
}
