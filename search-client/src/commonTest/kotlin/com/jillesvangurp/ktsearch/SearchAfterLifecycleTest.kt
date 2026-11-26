package com.jillesvangurp.ktsearch

import com.jillesvangurp.serializationext.DEFAULT_JSON
import com.jillesvangurp.searchdsls.querydsl.SortOrder
import com.jillesvangurp.searchdsls.querydsl.sort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SearchAfterLifecycleTest {
    @Test
    fun completedPagingClosesTheNewestSnapshot() = coRun {
        for (openSearch in listOf(false, true)) {
            val rest = LifecycleRecordingClient(openSearch)
            val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
            hits.map { it.id }.toList() shouldBe listOf("document-1", "document-2")
            rest.searchedIds shouldBe listOf("created", "page-1", "page-2")
            rest.closedIds shouldBe listOf("page-3")
        }
    }

    @Test
    fun emptyPagingClosesItsFirstResponseSnapshot() = coRun {
        val rest = LifecycleRecordingClient(true, pages = 0)
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        hits.toList() shouldBe emptyList()
        rest.closedIds shouldBe listOf("page-1")
    }

    @Test
    fun earlyCompletionClosesWithoutFetchingAnotherPage() = coRun {
        val rest = LifecycleRecordingClient(true)
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        hits.take(1).map { it.id }.toList() shouldBe listOf("document-1")
        rest.searchedIds shouldBe listOf("created")
        rest.closedIds shouldBe listOf("page-1")
    }

    @Test
    fun cancellationStillCompletesSuspendingCleanup() = coRun {
        val rest = LifecycleRecordingClient(true)
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        coroutineScope {
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                hits.collect { awaitCancellation() }
            }
            collecting.cancelAndJoin()
        }
        rest.closedIds shouldBe listOf("page-1")
    }

    @Test
    fun consumerFailureRemainsPrimaryWhenCleanupAlsoFails() = coRun {
        val rest = LifecycleRecordingClient(true)
        rest.failClose = true
        val failure = IllegalArgumentException("consumer failure")
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        val actual = shouldThrow<IllegalArgumentException> { hits.collect { throw failure } }
        actual shouldBe failure
        actual.suppressedExceptions.map { it.message } shouldBe listOf("cleanup failure")
        rest.closeAttempts shouldBe listOf("page-1")
    }

    @Test
    fun initialSearchFailureClosesTheCreatedSnapshot() = coRun {
        val rest = LifecycleRecordingClient(true)
        rest.failSearchAt = 1
        rest.failClose = true
        val actual = shouldThrow<IllegalStateException> {
            SearchClient(rest).searchAfter("source", 60.seconds)
        }
        actual.message shouldBe "search failure"
        actual.suppressedExceptions.map { it.message } shouldBe listOf("cleanup failure")
        rest.closeAttempts shouldBe listOf("created")
    }

    @Test
    fun laterSearchFailureClosesTheLatestSuccessfulSnapshot() = coRun {
        val rest = LifecycleRecordingClient(true)
        rest.failSearchAt = 2
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        shouldThrow<IllegalStateException> { hits.toList() }.message shouldBe "search failure"
        rest.closedIds shouldBe listOf("page-1")
    }

    @Test
    fun expiredSnapshotIsAlreadyReleased() = coRun {
        val rest = LifecycleRecordingClient(false, pages = 0)
        rest.cleanupStatus = 404
        val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
        hits.toList() shouldBe emptyList()
        rest.closeAttempts shouldBe listOf("page-1")
    }

    @Test
    fun cleanupAuthorizationAndServerErrorsStillSurface() = coRun {
        for (status in listOf(401, 403, 500)) {
            val rest = LifecycleRecordingClient(true, pages = 0)
            rest.cleanupStatus = status
            val (_, hits) = SearchClient(rest).searchAfter("source", 60.seconds)
            shouldThrow<RestException> { hits.toList() }.status shouldBe status
        }
    }

    @Test
    fun rejectedCustomSortDoesNotCreateASnapshot() = coRun {
        val rest = LifecycleRecordingClient(true)
        shouldThrow<IllegalStateException> {
            SearchClient(rest).searchAfter("source", 60.seconds) {
                sort { add("name", SortOrder.ASC) }
            }
        }
        rest.created shouldBe 0
        rest.closeAttempts shouldBe emptyList()
    }
}

private class LifecycleRecordingClient(
    private val openSearch: Boolean,
    private val pages: Int = 2,
) : RestClient {
    var created = 0
    var failSearchAt: Int? = null
    var failClose = false
    var cleanupStatus: Int? = null
    val searchedIds = mutableListOf<String>()
    val closeAttempts = mutableListOf<String>()
    val closedIds = mutableListOf<String>()
    override suspend fun nextNode() = Node("localhost", 9200)
    override fun close() = Unit
    override suspend fun doRequest(
        pathComponents: List<String>, httpMethod: HttpMethod, parameters: Map<String, Any>?,
        payload: String?, contentType: String, headers: Map<String, Any>?,
    ): RestResponse {
        val path = pathComponents.flatMap { it.split('/') }.filter { it.isNotEmpty() }
        val text = when {
            path.isEmpty() -> """{"name":"test","cluster_name":"test","cluster_uuid":"uuid","version":{"distribution":"${if (openSearch) "opensearch" else "elasticsearch"}","number":"${if (openSearch) "3.10.1" else "9.1.3"}","build_hash":"hash","build_date":"date","build_snapshot":false,"lucene_version":"10","minimum_wire_compatibility_version":"7","minimum_index_compatibility_version":"7"}}"""
            httpMethod == HttpMethod.Delete -> {
                val body = DEFAULT_JSON.parseToJsonElement(requireNotNull(payload)).jsonObject
                val id = if (openSearch) body.getValue("pit_id").jsonArray.single().jsonPrimitive.content
                    else body.getValue("id").jsonPrimitive.content
                closeAttempts.add(id)
                yield()
                currentCoroutineContext().ensureActive()
                if (failClose) error("cleanup failure")
                cleanupStatus?.let { status ->
                    val bytes = "{}".encodeToByteArray()
                    return when (status) {
                        404 -> RestResponse.Status4XX.NotFound(bytes, path.joinToString("/"), payload)
                        401 -> RestResponse.Status4XX.UnAuthorized(bytes, path.joinToString("/"), payload)
                        403 -> RestResponse.Status4XX.Forbidden(bytes, path.joinToString("/"), payload)
                        else -> RestResponse.Status5xx.InternalServerError(bytes)
                    }
                }
                closedIds.add(id)
                "{}"
            }
            path.last() == "_pit" || path.last() == "point_in_time" -> {
                created++
                """{"${if (openSearch) "pit_id" else "id"}":"created"}"""
            }
            path.last() == "_search" -> {
                val body = DEFAULT_JSON.parseToJsonElement(requireNotNull(payload)).jsonObject
                searchedIds.add(body.getValue("pit").jsonObject.getValue("id").jsonPrimitive.content)
                val n = searchedIds.size
                if (failSearchAt == n) error("search failure")
                val hit = if (n <= pages) """{"_index":"source","_id":"document-$n","sort":["$n"]}""" else ""
                """{"timed_out":false,"${if (n == 2) "point_in_time_id" else "pit_id"}":"page-$n","hits":{"total":{"value":$pages,"relation":"eq"},"hits":[$hit]}}"""
            }
            else -> error("Unexpected request")
        }
        return RestResponse.Status2XX.OK(text.encodeToByteArray())
    }
}
