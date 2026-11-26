package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class LogMessageTest {
    @Test
    fun nonFiniteAndInvalidNumericMdcValuesBecomeNull() {
        val context = LoggerContext()
        try {
            val event = LoggingEvent(
                "test", context.getLogger("example"), Level.INFO, "test", null, null
            ).apply { mdcPropertyMap = mapOf("ratio" to "NaN", "count" to "invalid") }
            val message = event.toLogMessage(null, setOf("count"), setOf("ratio"))
            message.mdc shouldBe mapOf("ratio" to kotlinx.serialization.json.JsonNull,
                "count" to kotlinx.serialization.json.JsonNull)
            DEFAULT_JSON.decodeFromString<LogMessage>(DEFAULT_JSON.encodeToString(message)) shouldBe message
        } finally {
            context.stop()
        }
    }

    @Test
    fun preservesFormattedMessageOriginalTimeAndCauseMessages() {
        val context = LoggerContext()
        try {
            val event = LoggingEvent(
                "test", context.getLogger("example"), Level.ERROR,
                "Processed {} records", IllegalStateException("outer", IllegalArgumentException("inner")),
                arrayOf(42)
            ).apply {
                timeStamp = 1234567890000L
                mdcPropertyMap = emptyMap()
            }
            val converted = event.toLogMessage(null, emptySet(), emptySet())
            converted.message shouldBe "Processed 42 records"
            converted.timestamp.toEpochMilliseconds() shouldBe 1234567890000L
            converted.exceptionList?.map { it.message } shouldBe listOf("outer", "inner")
        } finally {
            context.stop()
        }
    }

    @Test
    fun handlesExceptionsWithNullMessages() {
        val context = LoggerContext()
        try {
            val event = LoggingEvent(
                "test", context.getLogger("example"), Level.ERROR,
                "failed", RuntimeException(null as String?), null
            ).apply { mdcPropertyMap = emptyMap() }
            event.toLogMessage(null, emptySet(), emptySet()).exceptionList?.first()?.message shouldBe ""
        } finally {
            context.stop()
        }
    }

    @Test
    fun preservesContextAndCoercesMdcFields() {
        val context = LoggerContext().apply {
            name = "appender-test"
            putProperty("environment", "tests")
            putProperty("excluded", "hidden")
        }
        try {
            val event = LoggingEvent(
                "test", context.getLogger("example"), Level.WARN,
                "hello", null, null
            ).apply {
                threadName = "worker"
                mdcPropertyMap = mapOf(
                    "duration" to "42",
                    "ratio" to "0.5",
                    "label" to "example",
                    "empty" to ""
                )
            }

            val message = event.toLogMessage(
                Regex("environment"), setOf("duration"), setOf("ratio")
            )

            message.message shouldBe "hello"
            message.logger shouldBe "example"
            message.level shouldBe "WARN"
            message.thread shouldBe "worker"
            message.contextName shouldBe "appender-test"
            message.context shouldBe mapOf("environment" to "tests")
            message.mdc shouldBe mapOf(
                "duration" to JsonPrimitive(42L),
                "ratio" to JsonPrimitive(0.5),
                "label" to JsonPrimitive("example")
            )

            // Exercise serialization with the same runtime as search-client.
            val json = DEFAULT_JSON.encodeToString(message)
            DEFAULT_JSON.decodeFromString<LogMessage>(json) shouldBe message
            DEFAULT_JSON.parseToJsonElement(json).jsonObject[
                "@timestamp"
            ] shouldBe JsonPrimitive(message.timestamp.toString())
        } finally {
            context.stop()
        }
    }

    @Test
    fun retainsTheLogbackConfigurationClassName() {
        val appender = Class.forName(
            "com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender"
        ).getDeclaredConstructor().newInstance()

        (appender is KtSearchLogBackAppender) shouldBe true
    }
}
