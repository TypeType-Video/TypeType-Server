package dev.typetype.server.portability

import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipFile

class YoutubeTakeoutPortabilityAdapter : PortabilityAdapter {
    override val descriptor = PortabilityAdapterDescriptor(
        format = PortabilityFormat.YOUTUBE_TAKEOUT,
        adapterVersion = 2,
        capabilities = TAKEOUT_CATEGORIES.mapTo(linkedSetOf()) { category ->
            PortabilityCapability(category, setOf(PortabilityDirection.IMPORT), PortabilityFidelity.COMPLETE)
        },
        defaultExtension = "zip",
        contentType = "application/zip",
    )

    override fun detect(input: PortabilityInput): PortabilityDetection? {
        val archive = input.archive
        if (archive == null) {
            if (!YoutubeTakeoutJsonPortabilityReader.isCandidate(input.filename)) return null
            return PortabilityDetection(
                PortabilityFormat.YOUTUBE_TAKEOUT,
                null,
                90,
                "YouTube Takeout JSON activity file",
            )
        }
        val names = archive.names.map(String::lowercase)
        val youtubeFiles = names.count { name ->
            "youtube" in name && (name.endsWith(".csv") || name.endsWith(".html") || name.endsWith(".json"))
        }
        val jsonFiles = names.count { YoutubeTakeoutJsonPortabilityReader.isCandidate(it) }
        if (youtubeFiles + jsonFiles == 0) return null
        val hasTakeoutRoot = names.any { it.startsWith("takeout/") }
        return PortabilityDetection(
            PortabilityFormat.YOUTUBE_TAKEOUT,
            null,
            if (hasTakeoutRoot) 99 else 90,
            "YouTube Takeout CSV, HTML, or JSON activity files",
        )
    }

    override fun decode(input: PortabilityInput, sink: PortabilityRecordSink) {
        requireNotNull(detect(input)) { "Unsupported YouTube Takeout archive" }
        if (input.archive == null) {
            Files.newInputStream(input.path).use { YoutubeTakeoutJsonPortabilityReader.read(it, sink) }
            return
        }
        ZipFile(input.path.toFile()).use { zip ->
            val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
            YoutubeTakeoutCsvPortabilityReader.readManifests(zip, entries, sink)
            YoutubeTakeoutHtmlPortabilityReader.read(zip, entries, sink)
            entries.filter { YoutubeTakeoutJsonPortabilityReader.isCandidate(it.name) }.forEach { entry ->
                zip.getInputStream(entry).use { inputStream ->
                    YoutubeTakeoutJsonPortabilityReader.read(inputStream, sink)
                }
            }
            YoutubeTakeoutCsvPortabilityReader.readContent(zip, entries, sink)
        }
    }

    override fun encode(
        source: PortabilityRecordSource,
        output: OutputStream,
        categories: Set<PortabilityCategory>,
    ): Unit = error("YouTube Takeout export is not available")
}

private val TAKEOUT_CATEGORIES = setOf(
    PortabilityCategory.SUBSCRIPTIONS,
    PortabilityCategory.HISTORY,
    PortabilityCategory.PLAYLISTS,
    PortabilityCategory.WATCH_LATER,
    PortabilityCategory.FAVORITES,
)
