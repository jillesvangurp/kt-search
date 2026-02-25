package com.jillesvangurp.ktsearch

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RefreshTest : SearchTestBase() {
    @Test
    fun refreshIndexWithClient() = coRun {
        testDocumentIndex { index ->
            client.indexDocument(index, TestDocument("refresh-me").json())

            val response = client.refresh(index)

            response.shards.total shouldBeGreaterThan 0
            response.shards.failed shouldBe 0
        }
    }

    @Test
    fun refreshIndexWithRepository() = coRun {
        repo.createIndex(repo.indexNameOrWriteAlias) {}
        repo.index(TestDocument("refresh-me"))

        val response = repo.refresh()

        response.shards.total shouldBeGreaterThan 0
        response.shards.failed shouldBe 0
    }
}
