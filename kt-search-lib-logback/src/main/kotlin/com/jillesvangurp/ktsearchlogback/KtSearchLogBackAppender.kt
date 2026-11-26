package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient

/** Bulk indexes Logback events without requiring Spring or a metrics registry. */
@Suppress("MemberVisibilityCanBePrivate", "unused")
class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {
    /** Adds connection details to Logback's status messages. */
    var verbose = false
    /** Enables Ktor HTTP diagnostics; transport diagnostics are excluded from this appender. */
    var logElasticSearchCalls = false
    /** Search cluster host. */
    var host = "localhost"
    /** Search cluster HTTP port. */
    var port = 9200
    /** Optional HTTP basic authentication username. */
    var userName: String? = null
    /** Optional HTTP basic authentication password. */
    var password: String? = null
    /** Uses HTTPS when enabled. */
    var ssl = false
    /** Maximum batch accumulation time before sending, in seconds. */
    var flushSeconds = 5
    /** Maximum events in one bulk request. */
    var bulkMaxPageSize = 200
    /** Maximum queued events; overflow drops the oldest queued event. */
    var queueCapacity = 1000
    /** Retries after the initial attempt for transient request or item failures. */
    var maxRetries = 3
    /** Initial exponential retry delay, capped at five seconds. */
    var retryDelayMillis = 250L
    /** Deadline for each individual bulk request. */
    var requestTimeoutMillis = 10000L
    /** Total time allowed to drain and flush during stop. */
    var shutdownTimeoutMillis = 10000L
    /** Pre-provisioned data stream receiving the logs. */
    var dataStreamName = "applogs"
    /** Full-match filter for Logback context keys; blank includes all keys. */
    var contextVariableFilterRe = ""
    /** Comma-separated MDC fields coerced to JSON long values. */
    var coerceMdcFieldsToLong = ""
    /** Comma-separated MDC fields coerced to JSON double values. */
    var coerceMdcFieldsToDouble = ""
    /** Cumulative counters across start/stop cycles, available before Spring starts. */
    val statistics = LogAppenderStatistics()
    /** Whether the asynchronous indexing worker is active. */
    val isIndexerRunning: Boolean get() = logIndexer?.isRunning == true

    @Volatile
    private var logIndexer: LogIndexer? = null
    private var shutdownHook: Thread? = null
    private var variableFilter: Regex? = null
    private var longFields = emptySet<String>()
    private var doubleFields = emptySet<String>()
    internal var clientFactory: (() -> SearchClient)? = null

    /** Validates configuration and starts accepting events before client initialization. */
    @Synchronized
    override fun start() {
        if (isStarted) return
        try {
            require(host.isNotBlank() && port in 1..65535 && dataStreamName.isNotBlank())
            val config = IndexerConfig(
                bulkMaxPageSize, flushSeconds * 1000L, queueCapacity, maxRetries,
                retryDelayMillis, requestTimeoutMillis, shutdownTimeoutMillis
            )
            variableFilter = contextVariableFilterRe.takeIf { it.isNotBlank() }?.toRegex()
            longFields = coerceMdcFieldsToLong.fieldNames()
            doubleFields = coerceMdcFieldsToDouble.fieldNames()
            require(longFields.intersect(doubleFields).isEmpty()) {
                "An MDC field cannot be coerced to both long and double"
            }
            val indexer = LogIndexer(
                clientFactory ?: {
                    SearchClient(KtorRestClient(
                        host = host, port = port, user = userName, password = password,
                        https = ssl, logging = logElasticSearchCalls
                    ))
                }, dataStreamName, config, statistics
            ) { message, cause -> addWarn(message, cause) }
            logIndexer = indexer
            val hook = Thread({ stop() }, "kt-search-logback-shutdown")
            Runtime.getRuntime().addShutdownHook(hook)
            shutdownHook = hook
            super.start()
            indexer.start()
            if (verbose) addInfo("Log appender started for $host:$port/$dataStreamName")
        } catch (e: Exception) {
            super.stop()
            shutdownHook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
            shutdownHook = null
            logIndexer?.stop()
            logIndexer = null
            addError("Could not start log appender", e)
        }
    }

    /** Snapshots the event on its originating thread and offers it without network I/O. */
    override fun append(eventObject: ILoggingEvent?) {
        if (eventObject == null) return
        // Ktor logs HTTP requests through SLF4J: indexing those would generate more requests.
        if (eventObject.loggerName == "io.ktor.client.HttpClient") return
        val indexer = logIndexer ?: return
        try {
            indexer.enqueue(eventObject.toLogMessage(variableFilter, longFields, doubleFields))
        } catch (e: Exception) {
            statistics.received.incrementAndGet()
            statistics.failed.incrementAndGet()
            addWarn("Could not convert a logging event", e)
        }
    }

    /** Stops accepting events, removes its shutdown hook, and drains within the deadline. */
    override fun stop() {
        val indexer = synchronized(this) {
            super.stop()
            shutdownHook?.let { runCatching { Runtime.getRuntime().removeShutdownHook(it) } }
            shutdownHook = null
            logIndexer.also { logIndexer = null }
        }
        // Never hold the appender monitor while waiting for a network operation.
        indexer?.stop()
    }
}

private fun String.fieldNames() = split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
