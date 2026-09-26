package dev.typetype.server.portability

internal class PortabilityBatchRecordSource(
    private val category: PortabilityCategory,
    private val records: List<PortabilityRecord>,
) : PortabilityRecordSource {
    init {
        require(records.all { it.category == category })
    }

    override fun categories(): Set<PortabilityCategory> = setOf(category)

    override fun counts(): Map<PortabilityCategory, Long> = mapOf(category to records.size.toLong())

    override fun forEach(category: PortabilityCategory, block: (PortabilityRecord) -> Unit) {
        if (category == this.category) records.forEach(block)
    }
}
