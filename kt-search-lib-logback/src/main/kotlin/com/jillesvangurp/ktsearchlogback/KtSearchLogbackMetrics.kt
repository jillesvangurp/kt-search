package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.Appender
import ch.qos.logback.core.spi.AppenderAttachable
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Optional Micrometer bridge for appenders already initialized by Logback.
 * Register as a Spring [MeterBinder] bean after logging initialization; counters
 * include early startup events. Micrometer must be supplied by the application.
 * Recreate this binder after replacing the logging context or its appenders.
 */
class KtSearchLogbackMetrics(
    private val context: LoggerContext = LoggerFactory.getILoggerFactory() as LoggerContext,
) : MeterBinder, AutoCloseable {
    private val meters = IdentityHashMap<MeterRegistry, MutableList<Meter>>()

    /** Binds each attached appender once, including appenders behind async wrappers. */
    @Synchronized
    override fun bindTo(registry: MeterRegistry) {
        if (meters.containsKey(registry)) return
        val registered = mutableListOf<Meter>()
        meters[registry] = registered
        val seen = Collections.newSetFromMap(IdentityHashMap<Appender<*>, Boolean>())
        fun visit(appender: Appender<*>) {
            if (!seen.add(appender)) return
            if (appender is KtSearchLogBackAppender) {
                val name = appender.name ?: "unnamed"
                val stats = appender.statistics
                fun counter(suffix: String, description: String, value: (LogAppenderStatistics) -> Double) {
                    registered.add(FunctionCounter.builder("ktsearch.logback.events.$suffix", stats, value)
                        .description(description).tag("appender", name).register(registry))
                }
                counter("received", "Events offered, including dropped events") { it.receivedCount.toDouble() }
                counter("indexed", "Events acknowledged by the search cluster") { it.indexedCount.toDouble() }
                counter("failed", "Permanently rejected or exhausted events") { it.failedCount.toDouble() }
                counter("dropped", "Events dropped or unconfirmed at shutdown") { it.droppedCount.toDouble() }
                counter("retried", "Event retry attempts") { it.retriedCount.toDouble() }
                registered.add(Gauge.builder("ktsearch.logback.events.pending", stats) { it.pendingCount.toDouble() }
                    .description("Events queued or in flight").tag("appender", name).register(registry))
                registered.add(Gauge.builder("ktsearch.logback.active", appender) {
                    if (it.isIndexerRunning) 1.0 else 0.0
                }.description("Whether the indexing worker is active").tag("appender", name).register(registry))
            }
            if (appender is AppenderAttachable<*>) {
                appender.iteratorForAppenders().forEachRemaining { visit(it) }
            }
        }
        context.loggerList.forEach { logger -> logger.iteratorForAppenders().forEachRemaining { visit(it) } }
    }

    /** Removes this binder's meters when its Spring context or registry is closed. */
    @Synchronized
    override fun close() {
        meters.forEach { (registry, registered) -> registered.forEach { registry.remove(it) } }
        meters.clear()
    }
}
