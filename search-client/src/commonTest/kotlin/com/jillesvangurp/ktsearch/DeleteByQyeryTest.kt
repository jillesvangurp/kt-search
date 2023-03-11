package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DeleteByQyeryTest: SearchTestBase() {

    @Test
    fun shouldDeleteByQuery() = coRun {
        val index = testDocumentIndex()
        client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor)
        client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor)

        val resp = client.deleteByQuery(index) {
            query = matchAll()
        }

        resp.deleted shouldBe 2
    }
}