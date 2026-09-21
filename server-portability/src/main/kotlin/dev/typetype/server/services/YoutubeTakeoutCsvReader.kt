package dev.typetype.server.services

import java.io.BufferedReader
import java.io.PushbackReader

object YoutubeTakeoutCsvReader {
    fun parse(reader: BufferedReader): Pair<List<String>, List<List<String>>> {
        var header = emptyList<String>()
        val rows = mutableListOf<List<String>>()
        forEach(reader, { header = it }) { row ->
            require(rows.size < MAX_COLLECTED_ROWS) { "CSV contains too many rows" }
            rows += row
        }
        return header to rows
    }

    fun forEach(
        reader: BufferedReader,
        onHeader: (List<String>) -> Unit,
        onRow: (List<String>) -> Unit,
    ) {
        var headerSeen = false
        parseRecords(reader) { record ->
            if (record.none(String::isNotBlank)) return@parseRecords
            if (!headerSeen) {
                onHeader(record.mapIndexed { index, value -> if (index == 0) value.removePrefix("\uFEFF") else value })
                headerSeen = true
            } else {
                onRow(record)
            }
        }
    }

    private fun parseRecords(source: BufferedReader, block: (List<String>) -> Unit) {
        val reader = PushbackReader(source, 1)
        val row = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var hasData = false
        while (true) {
            val value = reader.read()
            if (value == -1) break
            val c = value.toChar()
            hasData = true
            when {
                c == '"' && quoted -> {
                    val next = reader.read()
                    if (next == '"'.code) current.append('"') else {
                        quoted = false
                        if (next != -1) reader.unread(next)
                    }
                }
                c == '"' -> quoted = true
                c == ',' && !quoted -> {
                    row.addField(current)
                }
                (c == '\n' || c == '\r') && !quoted -> {
                    if (c == '\r') {
                        val next = reader.read()
                        if (next != '\n'.code && next != -1) reader.unread(next)
                    }
                    row.addField(current)
                    block(row.toList())
                    row.clear()
                    hasData = false
                }
                else -> current.append(c)
            }
            require(current.length <= MAX_FIELD_CHARS) { "CSV field is too large" }
            require(row.size <= MAX_COLUMNS) { "CSV contains too many columns" }
        }
        require(!quoted) { "CSV contains an unterminated quoted field" }
        if (hasData || current.isNotEmpty() || row.isNotEmpty()) {
            row.addField(current)
            block(row.toList())
        }
    }

    private fun MutableList<String>.addField(value: StringBuilder) {
        add(value.toString().trim())
        value.clear()
    }

    private const val MAX_COLUMNS = 256
    private const val MAX_FIELD_CHARS = 2 * 1024 * 1024
    private const val MAX_COLLECTED_ROWS = 100_000
}
