package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.TermsAgg
import com.jillesvangurp.searchdsls.querydsl.agg
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import kotlin.test.BeforeTest
import kotlin.test.Test

@Serializable
data class MockDoc(
    val name: String,
    val tags: List<String>? = null,
    val color: String? = null,
    val value: Long? = null,
    val timestamp: Long? = null
) {
    companion object {
        val mapping = IndexSettingsAndMappingsDSL().apply {
            mappings {
                text(MockDoc::name)
                keyword(MockDoc::color)
                keyword(MockDoc::tags)
                number<Long>(MockDoc::value)
                date(MockDoc::timestamp)
            }
        }
    }
}

class AggQueryTest : SearchTestBase() {
    val repository = client.repository(randomIndexName(),MockDoc.serializer())
    class Tags {
        companion object {
            val foo = "foo"
            val bar = "bar"
            val fooBar = "foobar"
        }
    }
    class Colors {
        companion object {
            val red = "red"
            val green = "green"
        }
    }

    @BeforeTest
    fun before() = coRun {
        repository.createIndex(MockDoc.mapping)
        repository.bulk {
            index(MockDoc("1", tags = listOf(Tags.bar), color = Colors.green))
            index(MockDoc("2", tags = listOf(Tags.foo), color = Colors.red))
            index(MockDoc("3", tags = listOf(Tags.foo, Tags.bar), color = Colors.red))
            index(MockDoc("4", tags = listOf(Tags.fooBar), color = Colors.green))
        }

    }
    @Test
    fun shouldDoTermsAgg() = coRun {
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            // allows us to use the aggSpec after the query runs
            agg("by_tag", TermsAgg("tags") {
                aggSize = 100
            })
        }

        response.aggregations shouldNotBe null

        val terms = response.aggregations.termsResult("by_tag")
        terms shouldNotBe null
        val buckets = terms?.decodeBuckets()!!

        buckets.size shouldBe 3
        buckets.counts()[Tags.bar] shouldBe 2
        buckets.counts()[Tags.fooBar] shouldBe 1
    }

    @Test
    fun nestedAggregationQuery() = coRun {
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            // allows us to use the aggSpec after the query runs
            agg("by_tag", TermsAgg(MockDoc::tags) {
                aggSize = 100
            }) {
                agg("by_color", TermsAgg(MockDoc::color))
            }
        }
        val buckets = response.aggregations.termsResult("by_tag")?.buckets!!
        buckets.size shouldBeGreaterThan 0
        buckets.forEach { b ->
            val subAgg = b.termsResult("by_color")
            subAgg shouldNotBe null
            subAgg?.buckets?.size!! shouldBeGreaterThan 0
        }
    }
}