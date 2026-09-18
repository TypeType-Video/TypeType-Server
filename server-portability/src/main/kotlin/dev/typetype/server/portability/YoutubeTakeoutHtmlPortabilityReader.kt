package dev.typetype.server.portability

import dev.typetype.server.services.YoutubeTakeoutActivitySignalService
import dev.typetype.server.services.YoutubeTakeoutHistoryParser
import dev.typetype.server.services.YoutubeTakeoutPathHints
import java.io.Reader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object YoutubeTakeoutHtmlPortabilityReader {
    fun read(zip: ZipFile, entries: List<ZipEntry>, sink: PortabilityRecordSink) {
        entries.asSequence().filter(::isYoutubeHtml).forEach { entry ->
            zip.getInputStream(entry).bufferedReader().use { reader ->
                val historyEntry = isHistoryEntry(entry)
                val write = { html: String ->
                    writeWindow(html, sink, includeActivitySignals = !historyEntry, historyEntry)
                }
                if (entry.size in 1..WHOLE_ENTRY_BYTES) write(reader.readText())
                else readWindows(reader, write)
            }
        }
    }

    private fun writeWindow(
        html: String,
        sink: PortabilityRecordSink,
        includeActivitySignals: Boolean,
        historyEntry: Boolean,
    ) {
        val parsedHistory = YoutubeTakeoutHistoryParser.parseWithDiagnostics(html, requireWatchedMarker = !historyEntry)
        parsedHistory.items.forEach { sink.write(it.toPortability()) }
        if (parsedHistory.invalidDates > 0) {
            sink.issue(
                PortabilityIssue(
                    PortabilityCategory.HISTORY,
                    "invalid_takeout_date",
                    "YouTube Takeout HTML rows with an invalid date were skipped",
                    parsedHistory.invalidDates,
                ),
            )
        }
        if (includeActivitySignals) {
            val (subscriptions, favorites) = YoutubeTakeoutActivitySignalService.parseHtml(html)
            subscriptions.forEach { sink.write(it.toPortability()) }
            favorites.forEach { sink.write(it.toPortability()) }
        }
    }

    private fun readWindows(reader: Reader, block: (String) -> Unit) {
        val buffer = CharArray(READ_CHARS)
        val window = StringBuilder(WINDOW_CHARS + READ_CHARS)
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            window.append(buffer, 0, read)
            if (window.length >= WINDOW_CHARS) {
                block(window.toString())
                window.delete(0, window.length - OVERLAP_CHARS)
            }
        }
        if (window.isNotEmpty()) block(window.toString())
    }

    private fun isYoutubeHtml(entry: ZipEntry): Boolean = YoutubeTakeoutPathHints.isYoutubeHtml(entry.name)

    private fun isHistoryEntry(entry: ZipEntry): Boolean = YoutubeTakeoutPathHints.isHistoryEntry(entry.name)

    private const val READ_CHARS = 32 * 1024
    private const val WINDOW_CHARS = 4 * 1024 * 1024
    private const val OVERLAP_CHARS = 256 * 1024
    private const val WHOLE_ENTRY_BYTES = 128L * 1024L * 1024L
}
