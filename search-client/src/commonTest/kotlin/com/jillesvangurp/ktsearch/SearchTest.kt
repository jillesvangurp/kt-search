package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.match
import io.kotest.inspectors.runTest
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class SearchTest: SearchTestBase() {

    @Test
    fun shouldSearch() = runTest {
        val index= testDocumentIndex()
        client.indexDocument(index, TestDocument("foo bar").json(), refresh = Refresh.WaitFor)
        client.indexDocument(index, TestDocument("fooo").json(), refresh = Refresh.WaitFor)
        val response = client.search(index,"")
        response.total shouldBe 2

        client.search(index) {
            trackTotalHits = "true"
            query = match(TestDocument::name,"bar")
        }.total shouldBe 1
    }
}