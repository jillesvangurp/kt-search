package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.IThrowableProxy
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive


/**
 * Opinionated representation of logback log messages optimized for indexing in a sane way
 */
@Serializable
data class LogMessage(
    val message: String,
    val logger: String,
    val thread: String? = null,
    val level: String,
    @SerialName("@timestamp")
    val timestamp: Instant = Clock.System.now(),
    val mdc: Map<String, JsonPrimitive>? = null,
    val context: Map<String, String>? = null,
    val contextName: String? = null,
    /** exception and any causes stored in a structured way that can be easily mapped and aggregated on */
    val exceptionList: List<LogException>? = null
)

/** Structured exception details stored with a [LogMessage]. */
@Serializable
data class LogException(val className: String, val message: String, val stackTrace: List<String>?)

/** Converts an exception and its cause chain to indexable details. */
fun IThrowableProxy?.toLogException(): List<LogException>? {
    return this?.let {
        val l = mutableListOf(
            LogException(
                it.className,
                it.message,
                it.stackTraceElementProxyArray.map { st -> st.stackTraceElement.toString() })
        )
        var c = it.cause
        val seen = mutableSetOf<IThrowableProxy>()
        while (c != null && !seen.contains(c)) {
            l.add(
                LogException(
                    c.className,
                    c.className,
                    c.stackTraceElementProxyArray.map { st -> st.stackTraceElement.toString() })
            )
            // prevent cycles
            seen.add(c)
            c = c.cause
        }
        l
    }
}

/** Converts this event, filtering context and coercing the selected MDC fields. */
fun ILoggingEvent.toLogMessage(variableFilter: Regex?, coerceLongFields: Set<String>, coerceDoubleFields: Set<String>): LogMessage {
    return LogMessage(
        message = message,
        logger = loggerName,
        thread = threadName,
        level = level.levelStr,
        mdc = mdcPropertyMap
            .takeIf { (it?.size ?: 0) > 0 }
            ?.filter { !it.value.isNullOrBlank() }
            ?.map { (k, v) ->
                when (k) {
                    in coerceLongFields -> {
                        k to JsonPrimitive(v.toLongOrNull())
                    }
                    in coerceDoubleFields -> {
                        k to JsonPrimitive(v.toDoubleOrNull())
                    }
                    else -> k to JsonPrimitive(v)
                }
            }?.toMap(),
        contextName = loggerContextVO?.name,
        exceptionList = throwableProxy.toLogException(),
        context = this.loggerContextVO?.propertyMap.takeIf { (it?.size ?: 0) > 0 }?.filter { (k, _) ->
            if (variableFilter != null) {
                k.matches(variableFilter)
            } else {
                true
            }
        }?.filter { !it.value.isNullOrBlank() }
    )
}
