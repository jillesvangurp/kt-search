package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulk
import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.TimeSource

/** Thread-safe counters collected before any metrics framework starts. */
class LogAppenderStatistics {
    internal val received = AtomicLong()
    internal val indexed = AtomicLong()
    internal val failed = AtomicLong()
    internal val dropped = AtomicLong()
    internal val retried = AtomicLong()

    /** Events offered to the indexer, including events dropped under load. */
    val receivedCount: Long get() = received.get()
    /** Events acknowledged by the cluster, including successful retry conflicts. */
    val indexedCount: Long get() = indexed.get()
    /** Events rejected permanently or after exhausting retries. */
    val failedCount: Long get() = failed.get()
    /** Events discarded on overflow, after stop, or without confirmation at shutdown. */
    val droppedCount: Long get() = dropped.get()
    /** Event retry attempts; one event can contribute more than once. */
    val retriedCount: Long get() = retried.get()
    /** Events queued or in flight; a concurrent scrape is an approximate snapshot. */
    val pendingCount: Long get() =
        (receivedCount - indexedCount - failedCount - droppedCount).coerceAtLeast(0)
}

internal data class IndexerConfig(
    val bulkSize: Int = 200,
    val flushMillis: Long = 5000,
    val queueCapacity: Int = 1000,
    val maxRetries: Int = 3,
    val retryDelayMillis: Long = 250,
    val requestTimeoutMillis: Long = 10000,
    val shutdownTimeoutMillis: Long = 10000,
) {
    init {
        require(bulkSize > 0 && flushMillis > 0 && queueCapacity > 0)
        require(maxRetries >= 0 && retryDelayMillis > 0)
        require(requestTimeoutMillis > 0 && shutdownTimeoutMillis > 0)
    }
}

/**
 * Buffers logs for a single indexing worker. [stop] drains accepted events within
 * a bounded deadline and closes the client. Delivery is best effort in memory;
 * a process crash or an ambiguous request across rollover can lose or duplicate logs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogIndexer internal constructor(
    private val clientFactory: () -> SearchClient,
    private val index: String,
    private val config: IndexerConfig,
    val statistics: LogAppenderStatistics,
    private val report: (String, Throwable?) -> Unit,
) {
    /** Starts buffering with the supplied [client], which this indexer owns. */
    constructor(client: SearchClient, index: String, bulkMaxPageSize: Int, flushSeconds: Int) : this(
        { client }, index,
        IndexerConfig(bulkSize = bulkMaxPageSize, flushMillis = flushSeconds * 1000L),
        LogAppenderStatistics(), { message, cause -> diagnostic(message, cause) }
    )

    private data class Pending(val message: LogMessage, val id: String = UUID.randomUUID().toString())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("kt-search-logback"))
    private val queue = Channel<Pending>(
        capacity = config.queueCapacity,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { recordDrop() }
    )
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        val batch = mutableListOf<Pending>()
        var client: SearchClient? = null
        try {
            client = clientFactory()
            while (true) {
                val first = queue.receiveCatching().getOrNull() ?: break
                batch.add(first)
                val started = TimeSource.Monotonic.markNow()
                while (batch.size < config.bulkSize) {
                    val remaining = config.flushMillis - started.elapsedNow().inWholeMilliseconds
                    if (remaining <= 0) break
                    // The timer only covers waiting for another event, never network I/O.
                    val next = select {
                        queue.onReceiveCatching { it }
                        onTimeout(remaining) { null }
                    }
                    if (next == null || next.isClosed) break
                    batch.add(next.getOrThrow())
                }
                sendBatch(client, batch)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            report("Log indexer stopped after an unexpected failure", e)
        } finally {
            batch.forEach { recordDrop() }
            queue.cancel()
            runCatching { client?.close() }.onFailure {
                report("Could not close the log client", it)
            }
        }
    }

    /** Whether the worker is active, including client initialization. */
    val isRunning: Boolean get() = worker.isActive

    internal fun start() { worker.start() }

    /** Offers an event without blocking the caller; overflow drops the oldest event. */
    fun enqueue(message: LogMessage): Boolean {
        statistics.received.incrementAndGet()
        val sent = queue.trySend(Pending(message)).isSuccess
        if (!sent) recordDrop()
        if (sent) worker.start()
        return sent
    }

    private fun recordDrop() {
        val count = statistics.dropped.incrementAndGet()
        if (count == 1L || count % 100 == 0L) {
            report("Log events dropped or unconfirmed: $count", null)
        }
    }

    private suspend fun sendBatch(client: SearchClient, batch: MutableList<Pending>) {
        var attempt = 0
        while (batch.isNotEmpty()) {
            try {
                val payload = buildString {
                    val iterator = batch.iterator()
                    while (iterator.hasNext()) {
                        val event = iterator.next()
                        val source = try {
                            DEFAULT_JSON.encodeToString(event.message)
                        } catch (e: Exception) {
                            iterator.remove()
                            statistics.failed.incrementAndGet()
                            report("Could not serialize a log event", e)
                            continue
                        }
                        append("""{"create":{"_id":"${event.id}"}}""")
                        append('\n')
                        append(source)
                        append('\n')
                    }
                }
                if (batch.isEmpty()) return
                val response = withTimeout(config.requestTimeoutMillis) {
                    client.bulk(payload, target = index)
                }
                val details = response.itemDetails
                check(details.size == batch.size) { "Bulk response item count does not match request" }
                val retry = mutableListOf<Pending>()
                batch.zip(details).forEach { (event, result) ->
                    val item = result.second
                    when {
                        item.status in 200..299 || (attempt > 0 && item.status == 409) ->
                            statistics.indexed.incrementAndGet()
                        transientStatus(item.status) -> retry.add(event)
                        else -> {
                            statistics.failed.incrementAndGet()
                            report("Log event rejected: HTTP ${item.status}, ${item.error?.type}", null)
                        }
                    }
                }
                batch.clear()
                batch.addAll(retry)
            } catch (e: Exception) {
                // A request timeout can be retried; cancellation by stop must propagate.
                currentCoroutineContext().ensureActive()
                if (e is CancellationException && e !is TimeoutCancellationException) throw e
                val retryable = when (e) {
                    is RestException -> transientStatus(e.status)
                    is IOException, is TimeoutCancellationException -> true
                    else -> false
                }
                if (!retryable) {
                    statistics.failed.addAndGet(batch.size.toLong())
                    batch.clear()
                    report("Log bulk request failed permanently", e)
                    return
                }
                report("Log bulk request failed; retrying within configured limits", e)
            }
            if (batch.isEmpty()) return
            if (attempt >= config.maxRetries) {
                statistics.failed.addAndGet(batch.size.toLong())
                batch.clear()
                report("Log events failed after exhausting ${config.maxRetries} retries", null)
                return
            }
            delay((config.retryDelayMillis.coerceAtMost(5000) *
                (1L shl attempt.coerceAtMost(10))).coerceAtMost(5000))
            statistics.retried.addAndGet(batch.size.toLong())
            attempt++
        }
    }

    /** Stops accepting events, drains the queue, and bounds the total shutdown wait. */
    fun stop() {
        queue.close()
        worker.start()
        runBlocking {
            if (withTimeoutOrNull(config.shutdownTimeoutMillis) { worker.join(); true } != true) {
                report("Log shutdown deadline exceeded; cancelling pending delivery", null)
                worker.cancel()
                queue.cancel()
            }
        }
        scope.cancel()
    }
}

private fun transientStatus(status: Int) = status in setOf(408, 429, 500, 502, 503, 504)
