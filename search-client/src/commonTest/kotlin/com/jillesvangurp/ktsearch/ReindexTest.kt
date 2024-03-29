package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.Refresh.WaitFor
import com.jillesvangurp.searchdsls.querydsl.Conflict.PROCEED
import com.jillesvangurp.searchdsls.querydsl.ReindexOperationType.INDEX
import com.jillesvangurp.searchdsls.querydsl.ReindexVersionType.EXTERNAL
import com.jillesvangurp.searchdsls.querydsl.term
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class ReindexTest : SearchTestBase() {
    private val sourceName = randomIndexName()
    private val destinationName = randomIndexName()

    @BeforeTest
    fun before() = coRun {
        createIndices()
    }

    @AfterTest
    fun after() = coRun {
        deleteIndices()
    }

    @Test
    fun basicReindex() = runTest {
        client.indexDocument(sourceName, TestDocument(name = "t1"), refresh = WaitFor)

        val response = client.reindex {
            conflicts = PROCEED
            source {
                index = sourceName
            }
            destination {
                index = destinationName
            }
        }

        response.shouldHave(total = 1, created = 1, batches = 1)
    }

    @Test
    fun basicReindexWithSpecificValue() = runTest {
        client.indexDocument(sourceName, TestDocument(name = "t1"), refresh = WaitFor)

        val response = client.reindex(
            refresh = false,
            timeout = 10.seconds,
            waitForActiveShards = "1",
            requestsPerSecond = 10,
            requireAlias = false,
            scroll = 10.seconds,
            slices = 3,
            maxDocs = 9
        ) {
            conflicts = PROCEED
            maxDocs = 9
            source {
                index = sourceName
                batchSize = 10
                fields("name")
            }
            destination {
                index = destinationName
                versionType = EXTERNAL
                operationType = INDEX
            }
        }

        response.shouldHave(total = 1, created = 1, batches = 1)
    }

    @Test
    fun reindexWithQuery() = runTest {
        client.indexDocument(sourceName, TestDocument(name = "t1"), refresh = WaitFor)
        client.indexDocument(sourceName, TestDocument(name = "t2"), refresh = WaitFor)

        val response = client.reindex {
            source {
                index = sourceName
                query = term("name", "t1")
            }
            destination {
                index = destinationName
            }
        }

        response.shouldHave(total = 1, created = 1, batches = 1)
    }

    @Test
    fun asyncReindex() = runTest {
        client.indexDocument(sourceName, TestDocument(name = "t1"), refresh = WaitFor)

        val taskId = client.reindexAsync {
            source {
                index = sourceName
            }
            destination {
                index = destinationName
            }
        }

        val taskResponse = client.getTask(taskId.value, waitForCompletion = true)
        val jsonResponse = requireNotNull(taskResponse["response"]?.jsonObject) { "response element is missing on $taskResponse"}
        val response = jsonResponse.parse(ReindexResponse.serializer())

        response.shouldHave(total = 1, created = 1, batches = 1)
    }


    private suspend fun deleteIndices() {
        client.deleteIndex(sourceName)
        client.deleteIndex(destinationName)
    }

    private suspend fun createIndices() {
        client.createIndex(sourceName, mapping = TestDocument.mapping)
        client.createIndex(destinationName, mapping = TestDocument.mapping)
    }

}

private fun ReindexResponse.shouldHave(
    timedOut: Boolean = false,
    total: Int = 0,
    updated: Int = 0,
    created: Int = 0,
    deleted: Int = 0,
    batches: Int = 0,
    versionConflicts: Int = 0,
    noops: Int = 0,
    retriesBulk: Int = 0,
    retriesSearch: Int = 0,
    failures: List<String> = emptyList(),
) {
    this.also {
        it.timedOut shouldBe timedOut
        it.total shouldBe total
        it.updated shouldBe updated
        it.created shouldBe created
        it.deleted shouldBe deleted
        it.batches shouldBe batches
        it.versionConflicts shouldBe versionConflicts
        it.noops shouldBe noops
        it.retries.bulk shouldBe retriesBulk
        it.retries.search shouldBe retriesSearch
        it.failures shouldBe failures
    }

}
