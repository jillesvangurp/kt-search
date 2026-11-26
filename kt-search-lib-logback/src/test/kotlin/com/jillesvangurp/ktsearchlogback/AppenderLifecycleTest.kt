package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.Level
import com.jillesvangurp.ktsearch.SearchClient
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.net.URLClassLoader
import java.io.File
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class AppenderLifecycleTest {
    @Test
    fun appenderCanLoadWithoutMicrometerOnTheRuntimeClasspath() {
        val urls = System.getProperty("logbackTestRuntimeClasspath", System.getProperty("java.class.path"))
            .split(File.pathSeparator).filterNot { it.contains("micrometer-") }
            .map { File(it).toURI().toURL() }.toTypedArray()
        URLClassLoader(urls, ClassLoader.getPlatformClassLoader()).use { loader ->
            val missing = runCatching { loader.loadClass("io.micrometer.core.instrument.MeterRegistry") }
            (missing.exceptionOrNull() is ClassNotFoundException) shouldBe true
            val type = loader.loadClass("com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender")
            val appender = type.getDeclaredConstructor().newInstance()
            type.getMethod("stop").invoke(appender)
            type.getMethod("isStarted").invoke(appender) shouldBe false
        }
    }

    private fun event(context: LoggerContext, message: String) = LoggingEvent(
        "test", context.getLogger("example"), Level.INFO, message, null, null
    ).apply { mdcPropertyMap = emptyMap() }

    @Test
    fun acceptsEventsDuringInitializationAndCanRestart() {
        val context = LoggerContext()
        val gate = CountDownLatch(1)
        val created = AtomicInteger()
        val rest = LogTestClient()
        val appender = KtSearchLogBackAppender().apply {
            setContext(context)
            bulkMaxPageSize = 1
            clientFactory = {
                check(gate.await(5, TimeUnit.SECONDS))
                created.incrementAndGet()
                SearchClient(rest)
            }
        }
        try {
            appender.start()
            appender.start()
            appender.doAppend(event(context, "startup"))
            appender.statistics.receivedCount shouldBe 1
            appender.statistics.pendingCount shouldBe 1
            gate.countDown()
            appender.stop()
            appender.stop()
            appender.isStarted shouldBe false
            created.get() shouldBe 1
            rest.closed.get() shouldBe 1
            appender.statistics.indexedCount shouldBe 1

            appender.start()
            appender.doAppend(event(context, "restart"))
            appender.stop()
            appender.statistics.indexedCount shouldBe 2
            created.get() shouldBe 2
            rest.closed.get() shouldBe 2
            rest.requests.flatMap { it.messages } shouldBe listOf("startup", "restart")
        } finally {
            gate.countDown()
            appender.stop()
            context.stop()
        }
    }

    @Test
    fun invalidConfigurationDoesNotStartTheClient() {
        val context = LoggerContext()
        val created = AtomicInteger()
        val appender = KtSearchLogBackAppender().apply {
            setContext(context)
            queueCapacity = 0
            clientFactory = { created.incrementAndGet(); SearchClient(LogTestClient()) }
        }
        try {
            appender.stop()
            appender.start()
            appender.isStarted shouldBe false
            created.get() shouldBe 0
            context.statusManager.copyOfStatusList.last().level shouldBe 2
        } finally {
            appender.stop()
            context.stop()
        }
    }

    @Test
    fun transportDiagnosticsAndStatusMessagesAreNotIndexed() {
        val context = LoggerContext()
        val rest = LogTestClient { request, _ -> request.ids.map { 400 } }
        val appender = KtSearchLogBackAppender().apply {
            setContext(context)
            bulkMaxPageSize = 1
            clientFactory = { SearchClient(rest) }
        }
        val root = context.getLogger("ROOT")
        root.addAppender(appender)
        try {
            appender.start()
            val diagnostic = event(context, "http request").apply { loggerName = "io.ktor.client.HttpClient" }
            appender.doAppend(diagnostic)
            appender.doAppend(event(context, "application event"))
            appender.stop()
            rest.requests.flatMap { it.messages } shouldBe listOf("application event")
            appender.statistics.failedCount shouldBe 1
            context.statusManager.copyOfStatusList.any { it.message.contains("HTTP 400") } shouldBe true
        } finally {
            appender.stop()
            context.stop()
        }
    }

    @Test
    fun metricsBindAfterStartupWithoutReinitializingTheAppender(): Unit = runBlocking {
        val context = LoggerContext()
        val rest = LogTestClient()
        val created = AtomicInteger()
        val appender = KtSearchLogBackAppender().apply {
            name = "es-out"
            setContext(context)
            bulkMaxPageSize = 1
            clientFactory = { created.incrementAndGet(); SearchClient(rest) }
        }
        // Exercise discovery through an async wrapper and duplicate logger references.
        val wrapper = AsyncAppender().apply { setContext(context); addAppender(appender) }
        context.getLogger("ROOT").addAppender(wrapper)
        context.getLogger("example").addAppender(appender)
        val registry = SimpleMeterRegistry()
        val binder = KtSearchLogbackMetrics(context)
        try {
            appender.start()
            appender.doAppend(event(context, "before Spring"))
            eventually(2.seconds) { appender.statistics.indexedCount shouldBe 1 }
            binder.bindTo(registry)
            binder.bindTo(registry)
            registry.meters.size shouldBe 7
            registry.get("ktsearch.logback.events.indexed").tag("appender", "es-out")
                .functionCounter().count() shouldBe 1.0
            appender.doAppend(event(context, "after Spring"))
            eventually(2.seconds) {
                registry.get("ktsearch.logback.events.indexed").functionCounter().count() shouldBe 2.0
            }
            created.get() shouldBe 1
            appender.stop()
            registry.get("ktsearch.logback.active").gauge().value() shouldBe 0.0
            registry.get("ktsearch.logback.events.pending").gauge().value() shouldBe 0.0
            binder.close()
            registry.meters.size shouldBe 0
        } finally {
            binder.close()
            registry.close()
            appender.stop()
            context.stop()
        }
    }
}
