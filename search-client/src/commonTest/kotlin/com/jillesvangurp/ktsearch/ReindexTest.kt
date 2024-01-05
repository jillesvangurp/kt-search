package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.Refresh.WaitFor
import com.jillesvangurp.searchdsls.querydsl.Conflict.PROCEED
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReindexTest : SearchTestBase() {

    @Test
    fun basicReindex() = runTest {
        val sourceName = randomIndexName()
        val destinationName = randomIndexName()
        client.createIndex(sourceName, mapping = TestDocument.mapping)
        client.createIndex(destinationName, mapping = TestDocument.mapping)

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

        client.deleteIndex(sourceName)
        client.deleteIndex(destinationName)

        response.apply {
            timedOut shouldBe false
            total shouldBe 1
            updated shouldBe 0
            created shouldBe 1
            deleted shouldBe 0
            batches shouldBe 1
            versionConflicts shouldBe 0
            noops shouldBe 0
            retries.bulk shouldBe 0
            retries.search shouldBe 0
            throttledMillis shouldBe 0
            failures.size shouldBe 0
        }
    }
}