package dev.typetype.server.portability

import kotlinx.coroutines.CancellationException
import java.io.FilterOutputStream
import java.io.OutputStream

class PortabilityProgressReporter(
    private val job: PortabilityJob,
    private val phase: PortabilityProgressPhase,
    private val unit: PortabilityProgressUnit,
    private val total: Long? = null,
    private val interval: Long = 100L,
) {
    private var processed = 0L
    private var published = -1L

    init {
        publish(force = true)
    }

    fun add(count: Long = 1L) {
        ensureActive()
        require(count >= 0L)
        processed = Math.addExact(processed, count)
        publish(force = false)
    }

    fun finish() {
        ensureActive()
        publish(force = true)
    }

    fun ensureActive() {
        if (job.isCancelled()) throw CancellationException("Portability job was cancelled")
    }

    private fun publish(force: Boolean) {
        if (!force && processed - published < interval) return
        job.updateProgress(PortabilityJobProgress(phase, unit, processed, total))
        published = processed
    }
}

fun portabilityProgressInterval(total: Long?): Long = total
    ?.let { (it / 100L).coerceIn(1L, 100L) }
    ?: 100L

class ProgressRecordSink(
    private val delegate: PortabilityRecordSink,
    private val progress: PortabilityProgressReporter,
) : PortabilityRecordSink {
    override fun markCategory(category: PortabilityCategory) {
        progress.ensureActive()
        delegate.markCategory(category)
    }

    override fun write(record: PortabilityRecord): PortabilityWriteResult {
        progress.ensureActive()
        return delegate.write(record).also { progress.add() }
    }

    override fun issue(issue: PortabilityIssue) {
        progress.ensureActive()
        delegate.issue(issue)
    }

    override fun putLookup(namespace: String, key: String, value: String) {
        progress.ensureActive()
        delegate.putLookup(namespace, key, value)
    }

    override fun lookup(namespace: String, key: String): String? {
        progress.ensureActive()
        return delegate.lookup(namespace, key)
    }

    fun count(category: PortabilityCategory): Long =
        (delegate as? PortabilityRecordSource)?.counts()?.get(category) ?: 0L
}

class ProgressOutputStream(
    output: OutputStream,
    private val progress: PortabilityProgressReporter,
) : FilterOutputStream(output) {
    override fun write(value: Int) {
        progress.ensureActive()
        out.write(value)
        progress.add()
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        progress.ensureActive()
        out.write(buffer, offset, length)
        progress.add(length.toLong())
    }
}
