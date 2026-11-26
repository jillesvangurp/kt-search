package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal data class LogRequest(val ids: List<String>, val messages: List<String>)

internal class LogTestClient(
    val failRequest: suspend (LogRequest, Int) -> RestResponse? = { _, _ -> null },
    val respond: suspend (LogRequest, Int) -> List<Int> = { request, _ -> request.ids.map { 201 } }
) : RestClient {
    val requests = Collections.synchronizedList(mutableListOf<LogRequest>())
    val closed = AtomicInteger()
    val active = AtomicInteger()
    val maxActive = AtomicInteger()
    override suspend fun nextNode() = Node("localhost", 9990)
    override fun close() { closed.incrementAndGet() }
    override suspend fun doRequest(
        pathComponents: List<String>, httpMethod: HttpMethod,
        parameters: Map<String, Any>?, payload: String?, contentType: String,
        headers: Map<String, Any>?
    ): RestResponse {
        check(pathComponents.joinToString("/").endsWith("_bulk"))
        val lines = payload!!.trim().lines().chunked(2)
        val request = LogRequest(
            lines.map { Json.parseToJsonElement(it[0]).jsonObject.getValue("create")
                .jsonObject.getValue("_id").jsonPrimitive.content },
            lines.map { Json.parseToJsonElement(it[1]).jsonObject.getValue("message").jsonPrimitive.content }
        )
        requests.add(request)
        val attempt = requests.size
        maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
        try {
            failRequest(request, attempt)?.let { return it }
            val statuses = respond(request, attempt)
            return RestResponse.Status2XX.OK(buildJsonObject {
                put("took", 0)
                put("errors", statuses.any { it >= 300 })
                putJsonArray("items") {
                    request.ids.zip(statuses).forEach { (id, status) ->
                        add(buildJsonObject {
                            putJsonObject("create") {
                                put("_index", "logs")
                                put("_id", id)
                                put("status", status)
                                if (status >= 300) putJsonObject("error") { put("type", "test_failure") }
                            }
                        })
                    }
                }
            }.toString().encodeToByteArray())
        } finally {
            active.decrementAndGet()
        }
    }
}

class LogIndexerTest {
    private fun message(value: String) = LogMessage(value, "test", level = "INFO")
    private fun indexer(
        client: LogTestClient,
        config: IndexerConfig = IndexerConfig(bulkSize = 2, flushMillis = 20, retryDelayMillis = 1),
        factory: () -> SearchClient = { SearchClient(client) },
    ) = LogIndexer(factory, "logs", config, LogAppenderStatistics()) { _, _ -> }

    @Test
    fun retriesWholeRequestsForRateLimitsAndServerFailures() {
        val rest = LogTestClient(failRequest = { _, attempt ->
            when (attempt) {
                1 -> RestResponse.Status4XX.TooManyRequests("{}".encodeToByteArray(), "_bulk", null)
                2 -> RestResponse.Status5xx.ServiceUnavailable("{}".encodeToByteArray())
                else -> null
            }
        })
        val indexer = indexer(rest, IndexerConfig(bulkSize = 1, retryDelayMillis = 1))
        indexer.enqueue(message("retry"))
        indexer.stop()
        rest.requests.size shouldBe 3
        rest.requests.map { it.ids }.distinct().size shouldBe 1
        indexer.statistics.indexedCount shouldBe 1
        indexer.statistics.retriedCount shouldBe 2
    }

    @Test
    fun permanentRequestFailuresAreNotRetried() {
        val rest = LogTestClient(failRequest = { _, _ ->
            RestResponse.Status4XX.UnAuthorized("{}".encodeToByteArray(), "_bulk", null)
        })
        val indexer = indexer(rest, IndexerConfig(bulkSize = 1))
        repeat(2) { indexer.enqueue(message("$it")) }
        indexer.stop()
        rest.requests.size shouldBe 2
        indexer.statistics.failedCount shouldBe 2
        indexer.statistics.retriedCount shouldBe 0
        indexer.statistics.pendingCount shouldBe 0
    }

    @Test
    fun initializationFailureDiscardsQueuedEventsAndStopsWorker() {
        val rest = LogTestClient()
        val gate = CountDownLatch(1)
        val indexer = indexer(rest) {
            check(gate.await(5, TimeUnit.SECONDS))
            error("initialization failed")
        }
        try {
            repeat(3) { indexer.enqueue(message("$it")) }
        } finally {
            gate.countDown()
            indexer.stop()
        }
        indexer.statistics.receivedCount shouldBe 3
        indexer.statistics.droppedCount shouldBe 3
        indexer.statistics.pendingCount shouldBe 0
        indexer.isRunning shouldBe false
    }

    @Test
    fun eventsAroundFlushDeadlinesAreNotDiscarded(): Unit = runBlocking {
        val rest = LogTestClient()
        val indexer = indexer(rest, IndexerConfig(bulkSize = 50, flushMillis = 2))
        try {
            repeat(8) {
                indexer.enqueue(message("$it"))
                delay(2)
            }
        } finally {
            indexer.stop()
        }
        indexer.statistics.indexedCount shouldBe 8
        indexer.statistics.droppedCount shouldBe 0
    }

    @Test
    fun slowBulkRequestsDrainTheWholeQueue() {
        val rest = LogTestClient { request, _ -> delay(120); request.ids.map { 201 } }
        val indexer = indexer(rest)
        try {
            repeat(6) { indexer.enqueue(message("event-$it")) shouldBe true }
        } finally {
            indexer.stop()
        }
        rest.requests.flatMap { it.messages } shouldContainExactly (0..5).map { "event-$it" }
        rest.maxActive.get() shouldBe 1
        rest.closed.get() shouldBe 1
        indexer.statistics.indexedCount shouldBe 6
        indexer.statistics.droppedCount shouldBe 0
        indexer.statistics.pendingCount shouldBe 0
        indexer.stop()
        rest.closed.get() shouldBe 1
    }

    @Test
    fun flushesAnIdlePartialBatchWithoutCancellingItsRequest(): Unit = runBlocking {
        val rest = LogTestClient { request, _ -> delay(120); request.ids.map { 201 } }
        val indexer = indexer(rest)
        try {
            indexer.enqueue(message("single"))
            eventually(2.seconds) { indexer.statistics.indexedCount shouldBe 1 }
            rest.requests.size shouldBe 1
        } finally {
            indexer.stop()
        }
    }

    @Test
    fun retriesOnlyTransientItemsInAPartialResponse() {
        val rest = LogTestClient { _, attempt -> if (attempt == 1) listOf(201, 429, 400) else listOf(201) }
        val indexer = indexer(rest, IndexerConfig(bulkSize = 3, retryDelayMillis = 1))
        repeat(3) { indexer.enqueue(message("$it")) }
        indexer.stop()
        rest.requests[1].ids shouldBe listOf(rest.requests[0].ids[1])
        indexer.statistics.indexedCount shouldBe 2
        indexer.statistics.failedCount shouldBe 1
        indexer.statistics.retriedCount shouldBe 1
        indexer.statistics.pendingCount shouldBe 0
    }

    @Test
    fun reusesIdsAfterAmbiguousTransportFailures() {
        val rest = LogTestClient { request, attempt ->
            if (attempt == 1) throw IOException("connection lost after sending")
            request.ids.map { 409 }
        }
        val indexer = indexer(rest)
        repeat(2) { indexer.enqueue(message("$it")) }
        indexer.stop()
        rest.requests[1].ids shouldBe rest.requests[0].ids
        indexer.statistics.indexedCount shouldBe 2
        indexer.statistics.retriedCount shouldBe 2
        indexer.statistics.failedCount shouldBe 0
    }

    @Test
    fun exhaustsTransientRetriesAndContinuesWithTheNextBatch() {
        val rest = LogTestClient { request, attempt -> request.ids.map { if (attempt <= 3) 503 else 201 } }
        val indexer = indexer(rest, IndexerConfig(bulkSize = 2, maxRetries = 2, retryDelayMillis = 1))
        repeat(4) { indexer.enqueue(message("$it")) }
        indexer.stop()
        rest.requests.size shouldBe 4
        indexer.statistics.failedCount shouldBe 2
        indexer.statistics.indexedCount shouldBe 2
        indexer.statistics.retriedCount shouldBe 4
        indexer.statistics.pendingCount shouldBe 0
    }

    @Test
    fun requestTimeoutsAreBoundedAndRetried() {
        val rest = LogTestClient { request, _ -> delay(10000); request.ids.map { 201 } }
        val indexer = indexer(rest, IndexerConfig(
            bulkSize = 1, requestTimeoutMillis = 30, maxRetries = 1, retryDelayMillis = 1
        ))
        indexer.enqueue(message("timeout"))
        val start = TimeSource.Monotonic.markNow()
        indexer.stop()
        start.elapsedNow().inWholeMilliseconds shouldBeLessThan 1000
        rest.requests.size shouldBe 2
        indexer.statistics.failedCount shouldBe 1
        indexer.statistics.retriedCount shouldBe 1
        rest.closed.get() shouldBe 1
    }

    @Test
    fun shutdownDeadlineCountsUnconfirmedEventsAndClosesClient(): Unit = runBlocking {
        val started = CompletableDeferred<Unit>()
        val rest = LogTestClient { request, _ -> started.complete(Unit); delay(10000); request.ids.map { 201 } }
        val indexer = indexer(rest, IndexerConfig(bulkSize = 1, shutdownTimeoutMillis = 50))
        repeat(4) { indexer.enqueue(message("$it")) }
        withTimeout(2000) { started.await() }
        val start = TimeSource.Monotonic.markNow()
        indexer.stop()
        start.elapsedNow().inWholeMilliseconds shouldBeLessThan 1000
        eventually(2.seconds) {
            rest.closed.get() shouldBe 1
            indexer.statistics.droppedCount shouldBe 4
            indexer.statistics.pendingCount shouldBe 0
        }
    }

    @Test
    fun overflowDropsTheOldestQueuedEventsAndReportsLoss() {
        val gate = CountDownLatch(1)
        val rest = LogTestClient()
        val indexer = indexer(rest, IndexerConfig(queueCapacity = 2)) {
            check(gate.await(5, TimeUnit.SECONDS))
            SearchClient(rest)
        }
        try {
            repeat(5) { indexer.enqueue(message("$it")) }
            indexer.statistics.receivedCount shouldBe 5
            indexer.statistics.droppedCount shouldBe 3
        } finally {
            gate.countDown()
            indexer.stop()
        }
        rest.requests.flatMap { it.messages } shouldContainExactly listOf("3", "4")
        indexer.statistics.indexedCount shouldBe 2
        indexer.enqueue(message("after stop")) shouldBe false
        indexer.statistics.droppedCount shouldBe 4
        indexer.statistics.pendingCount shouldBe 0
    }

    @Test
    fun concurrentProducersKeepEveryAcceptedEvent(): Unit = runBlocking {
        val rest = LogTestClient()
        val indexer = indexer(rest, IndexerConfig(bulkSize = 3))
        try {
            (0..7).map { launch(Dispatchers.Default) { indexer.enqueue(message("$it")) } }.joinAll()
        } finally {
            indexer.stop()
        }
        rest.requests.flatMap { it.messages }.sorted() shouldBe (0..7).map { "$it" }.sorted()
        rest.maxActive.get() shouldBe 1
        indexer.statistics.indexedCount shouldBe 8
    }
}
