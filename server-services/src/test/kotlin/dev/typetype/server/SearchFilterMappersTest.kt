package dev.typetype.server

import dev.typetype.server.models.ExtractionResult
import dev.typetype.server.services.PipePipeSearchService
import dev.typetype.server.services.SearchFilterResolution
import dev.typetype.server.services.VALID_SERVICE_IDS
import dev.typetype.server.services.defaultSearchFilter
import dev.typetype.server.services.defaultSearchFilters
import dev.typetype.server.services.findSearchFilter
import dev.typetype.server.services.resolveSearchFilters
import dev.typetype.server.services.toSearchContentFilterOptions
import dev.typetype.server.services.toSearchFilterGroups
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.services.youtube.search.filter.YoutubeSearchSortFilter
import org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf.DateFilter
import org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf.LenFilter
import org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf.SortOrder
import org.schabi.newpipe.extractor.services.youtube.search.filter.protobuf.TypeFilter

class SearchFilterMappersTest {
    private val factory = NewPipe.getService(0).searchQHFactory
    private val content = factory.availableContentFilter.defaultSearchFilter()
    private val filters = factory.getContentFilterSortFilterVariant(content.first().identifier)

    @Test
    fun `YouTube filters expose exclusive and multi-select groups`() {
        val groups = filters.toSearchFilterGroups()

        assertEquals(listOf("sortby", "upload_date", "duration", "features"), groups.map { it.label })
        assertFalse(groups.first { it.label == "duration" }.multiSelect)
        assertTrue(groups.first { it.label == "features" }.multiSelect)
        assertTrue(groups.first { it.label == "sortby" }.options.first().isDefault)
        assertFalse(groups.first { it.label == "features" }.options.first().isDefault)
    }

    @Test
    fun `YouTube filters resolve selections across groups`() {
        val groups = filters.toSearchFilterGroups()
        val values = listOf(
            groups.option("sortby", "sort_view"),
            groups.option("upload_date", "past_week"),
            groups.option("duration", "short_video"),
            groups.option("features", "HD"),
            groups.option("features", "Subtitles"),
        )

        val resolution = filters.resolveSearchFilters(values)

        assertInstanceOf(SearchFilterResolution.Valid::class.java, resolution)
        assertEquals(5, (resolution as SearchFilterResolution.Valid).items.size)
        val url = factory.fromQuery("kotlin", content, resolution.items).url
        val request = YoutubeSearchSortFilter().decodeSp(url.substringAfter("&sp="))

        assertEquals(SortOrder.views.value.toLong(), request.sorted)
        assertEquals(DateFilter.week.value.toLong(), request.filter.date)
        assertEquals(LenFilter.duration_short.value.toLong(), request.filter.length)
        assertTrue(request.filter.is_hd)
        assertTrue(request.filter.subtitles)
    }

    @Test
    fun `YouTube content filters retain their type with default selections`() {
        val contentFilters = factory.availableContentFilter
        val videoValue = contentFilters.toSearchContentFilterOptions()
            .first { it.label.endsWith("videos") }
            .value
        val videoContent = contentFilters.findSearchFilter(videoValue)
        val videoFilters = factory.getContentFilterSortFilterVariant(videoContent.first().identifier)

        val url = factory.fromQuery("kotlin", videoContent, videoFilters.defaultSearchFilters()).url
        val request = YoutubeSearchSortFilter().decodeSp(url.substringAfter("&sp="))

        assertEquals(TypeFilter.video.value.toLong(), request.filter.type)
    }

    @Test
    fun `YouTube filters reject conflicting exclusive selections`() {
        val sortOptions = filters.toSearchFilterGroups().first { it.label == "sortby" }.options

        val resolution = filters.resolveSearchFilters(sortOptions.take(2).map { it.value })

        assertInstanceOf(SearchFilterResolution.Invalid::class.java, resolution)
        assertTrue((resolution as SearchFilterResolution.Invalid).message.contains("sortby"))
    }

    @Test
    fun `YouTube filters reject unknown selections`() {
        val resolution = filters.resolveSearchFilters(listOf("unknown"))

        assertInstanceOf(SearchFilterResolution.Invalid::class.java, resolution)
    }

    @Test
    fun `all supported services expose filter capabilities`() = runTest {
        val service = PipePipeSearchService()

        VALID_SERVICE_IDS.forEach { serviceId ->
            assertInstanceOf(
                ExtractionResult.Success::class.java,
                service.filters(serviceId, null),
                "service $serviceId",
            )
        }
    }
}

private fun List<dev.typetype.server.models.SearchFilterGroup>.option(group: String, label: String): String =
    first { it.label == group }.options.first { it.label == label }.value
