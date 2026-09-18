package dev.typetype.server.models

data class YoutubeTakeoutCsvRows(
    val subscriptionsRows: List<List<String>>,
    val subscriptionsHeader: List<String>,
    val playlistsRows: List<List<String>>,
    val playlistsHeader: List<String>,
    val playlistItemsRows: List<List<String>>,
    val playlistItemsHeader: List<String>,
    val warnings: List<String>,
)
