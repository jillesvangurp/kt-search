package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.TermsAgg
import com.jillesvangurp.searchdsls.querydsl.agg
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AggQueryTest: SearchTestBase() {
    @Test
    fun shouldDoTermsAgg() = coTest {
        repo.createIndex(TestDocument.mapping)
        val tags = object {
            val foo = "foo"
            val bar = "bar"
        }
        repo.bulk {
            index(TestDocument("1", tags = listOf(tags.bar)).json())
            index(TestDocument("2", tags = listOf(tags.foo)).json())
            index(TestDocument("3", tags = listOf(tags.foo,tags.bar)).json())
        }
        val (r, _) = repo.search {
            resultSize = 0 // we only care about the aggs
            agg("by_tag", TermsAgg("tags") {
                aggSize = 100
            })
        }
        val buckets = r.aggregations["by_tag"]?.asBucketAggregationResult()?.buckets!!
        buckets.size shouldBe 2
    }
}