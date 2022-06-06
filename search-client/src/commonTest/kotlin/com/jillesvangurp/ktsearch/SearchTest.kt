package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.count
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class SearchTest: SearchTestBase() {

    @Test
    fun shouldSearch() = coTest {
        val index= testDocumentIndex()
        client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor)
        client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor)
        val response = client.search(index,"")
        response.total shouldBe 2

        client.search(index) {
            trackTotalHits = "true"
            query = match(TestDocument::name,"bar")
        }.total shouldBe 1
    }

    @Test
    fun shouldDoScrollingSearch() = coTest {
        val index= testDocumentIndex()
        client.bulk(target = index, refresh = Refresh.WaitFor) {
            (1..20).forEach {
                index(TestDocument("doc $it").json())
            }
        }
        val resp = client.search(index, scroll = "1m") {
            resultSize=3
            query = matchAll()
        }
        client.scroll(resp).count() shouldBe 20
    }

    @Test
    fun shouldDoSearchAfter() = coTest {
        val index= testDocumentIndex()
        client.bulk(target = index, refresh = Refresh.WaitFor) {
            (1..20).forEach {
                index(TestDocument("doc $it").json())
            }
        }
        val q = SearchDSL().apply{
            resultSize=3
            query = matchAll()
        }
        val (resp,hits) = client.searchAfter(index,1.minutes,q)
        resp.total shouldBe 20
        hits.count() shouldBe 20
    }
}