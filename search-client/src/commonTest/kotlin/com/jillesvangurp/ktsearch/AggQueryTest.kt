package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.*
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

@Serializable
data class MockDoc(
    val name: String,
    val tags: List<String>? = null,
    val color: String? = null,
    val value: Long? = null,
    val timestamp: Instant? = null
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
    val repository = client.repository(randomIndexName(), MockDoc.serializer())

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

    // FIXME @BeforeTest does not seem to work with mocha / node.js and async suspend; so call directly
    suspend fun before()  {
        repository.createIndex(MockDoc.mapping)
        repository.bulk {
            val now = Clock.System.now()
            index(MockDoc(name = "1", tags = listOf(Tags.bar), value = 1, color = Colors.green, timestamp = now))
            index(MockDoc(name = "2", tags = listOf(Tags.foo), value = 2, color = Colors.red, timestamp = now - 1.days))
            index(MockDoc(name = "3", tags = listOf(Tags.foo, Tags.bar), value = 3, color = Colors.red, timestamp = now - 5.days))
            index(MockDoc(name = "4", tags = listOf(Tags.fooBar), value = 4, color = Colors.green, timestamp = now - 10.days))
        }
    }

    @Test
    fun shouldDoTermsAgg() = coRun {
        before()
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
        val buckets = terms.parsedBuckets.map { it.parsed }

        buckets.size shouldBe 3
        buckets.counts()[Tags.bar] shouldBe 2
        buckets.counts()[Tags.fooBar] shouldBe 1
    }

    @Test
    fun shouldDoRangesAgg() = coRun {
        before()
        val aggName = "value_ranges"
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            agg(aggName, RangesAgg("value") {
                ranges = listOf(
                    AggRange.create {
                        key = "low"
                        to = 3.0
                    },
                    AggRange.create {
                        key = "high"
                        from = 3.0
                    }
                )
            })
        }

        response.aggregations shouldNotBe null

        val rangesAgg = response.aggregations.rangesResult(aggName)
        rangesAgg shouldNotBe null
        val rangeCounts = rangesAgg.parsedBuckets.map { it.parsed }.rangeCounts()

        rangeCounts.size shouldBe 2
        rangeCounts["low"] shouldBe 2
        rangeCounts["high"] shouldBe 2
    }

    @Test
    fun nestedAggregationQuery() = coRun {
        before()
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            // allows us to use the aggSpec after the query runs
            agg("by_tag", TermsAgg(MockDoc::tags) {
                aggSize = 100
            }) {
                agg("by_color", TermsAgg(MockDoc::color))
            }
        }
        val buckets = response.aggregations.termsResult("by_tag").buckets
        buckets.size shouldBeGreaterThan 0
        buckets.forEach { b ->
            val subAgg = b.termsResult("by_color")
            subAgg shouldNotBe null
            subAgg.buckets.size shouldBeGreaterThan 0
        }
    }

    @Test
    fun dateHistogramAgg() = coRun {
        before()
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            // allows us to use the aggSpec after the query runs
            agg("by_day", DateHistogramAgg(MockDoc::timestamp) {
                calendarInterval = "1d"
                minDocCount = 1
            }) {
                agg("by_color", TermsAgg(MockDoc::color))
            }
        }
        val dt = response.aggregations.dateHistogramResult("by_day")
        dt shouldNotBe null
        dt.parsedBuckets.forEach {
            it.parsed.docCount shouldBeGreaterThan 0
        }
        val total = dt.let { result -> result.parsedBuckets.flatMap { b -> b.aggregations.termsResult("by_color").parsedBuckets.map { tb -> tb.parsed.docCount } } }.sum()
        total shouldBe 4
    }

    @Test
    fun minMaxScriptAgg() = coRun {
        before()
        val response = repository.search {
            resultSize = 0 // we only care about the aggs
            // allows us to use the aggSpec after the query runs
            agg("by_color", TermsAgg(MockDoc::color)) {
                agg("min_time", MinAgg(MockDoc::timestamp))
                agg("max_time", MaxAgg(MockDoc::timestamp))
                agg("time_span", BucketScriptAgg {
                    script = "params.max - params.min"
                    bucketsPath = BucketsPath {
                        this["min"] = "min_time"
                        this["max"] = "max_time"
                    }
                })
            }
            agg("span_stats", ExtendedStatsBucketAgg {
                bucketsPath = "by_color>time_span"
            })
        }

        response.aggregations.termsResult("by_color").buckets.forEach { b ->
            b.bucketScriptResult("time_span").value shouldBeGreaterThan 0.0
        }
        response.aggregations.extendedStatsBucketResult("span_stats").avg shouldBeGreaterThan 0.0
    }

    @Test
    fun topHitsAgg() = coRun {
        before()
        val response = repository.search {
            resultSize = 0
            agg("by_color", TermsAgg(MockDoc::color)) {
                agg("top", TopHitsAgg() {
                    this.resultSize = 5
                })
            }
        }
        response.aggregations.termsResult("by_color").buckets.forEach { b ->
            b.topHitResult("top").hits.hits.size shouldBeGreaterThan 0
        }
    }
}
