package dev.typetype.server.portability

class PortabilityRegistry(adapters: List<PortabilityAdapter>) {
    private val adapters = adapters.toList()
    private val byFormat = adapters.associateBy { it.descriptor.format }

    init {
        require(adapters.isNotEmpty()) { "At least one portability adapter is required" }
        require(byFormat.size == adapters.size) { "Only one adapter per format can be registered" }
    }

    fun descriptors(): List<PortabilityAdapterDescriptor> = adapters.map { it.descriptor }

    fun adapter(format: PortabilityFormat): PortabilityAdapter =
        byFormat[format] ?: throw UnsupportedPortabilityFormatException(format)

    fun detect(
        input: PortabilityInput,
        formatHint: PortabilityFormat? = null,
    ): Pair<PortabilityAdapter, PortabilityDetection> {
        if (formatHint != null) {
            val adapter = adapter(formatHint)
            val detection = adapter.detect(input)
                ?: throw PortabilityFormatMismatchException(formatHint)
            return adapter to detection
        }
        val matches = adapters.asSequence()
            .filter(PortabilityAdapter::autoDetect)
            .mapNotNull { adapter -> adapter.detect(input)?.let { adapter to it } }
            .sortedByDescending { it.second.confidence }
            .toList()
        val best = matches.firstOrNull() ?: throw UnknownPortabilityFormatException()
        require(best.second.confidence in 1..100) { "Adapter returned an invalid confidence" }
        val second = matches.getOrNull(1)
        if (second != null && best.second.confidence - second.second.confidence < MIN_CONFIDENCE_GAP) {
            throw AmbiguousPortabilityFormatException(best.second, second.second)
        }
        return best
    }

    private companion object {
        const val MIN_CONFIDENCE_GAP = 10
    }
}

sealed class PortabilityContractException(val code: String, message: String) : IllegalArgumentException(message)

class UnknownPortabilityFormatException :
    PortabilityContractException("portability_format_unknown", "Unable to detect the backup format")

class UnsupportedPortabilityFormatException(format: PortabilityFormat) :
    PortabilityContractException("portability_format_unsupported", "No adapter is registered for ${format.wireName}")

class AmbiguousPortabilityFormatException(first: PortabilityDetection, second: PortabilityDetection) :
    PortabilityContractException(
        "portability_format_ambiguous",
        "Backup matches both ${first.format.wireName} and ${second.format.wireName}",
    )

class PortabilityFormatMismatchException(format: PortabilityFormat) :
    PortabilityContractException("portability_format_mismatch", "Backup does not match ${format.wireName}")

class PortabilityUploadTooLargeException :
    PortabilityContractException("portability_upload_too_large", "Backup exceeds the upload limit")

fun portabilityErrorCode(error: Exception): String = when (error) {
    is PortabilityContractException -> error.code
    is PortabilityJobNotFoundException -> "portability_job_not_found"
    is IllegalStateException -> "portability_invalid_state"
    is IllegalArgumentException -> "portability_invalid_input"
    else -> "portability_failed"
}

fun portabilityErrorMessage(error: Exception): String = when (error) {
    is PortabilityContractException, is IllegalArgumentException ->
        error.message ?: "Invalid portability data"
    else -> "Portability operation failed"
}
