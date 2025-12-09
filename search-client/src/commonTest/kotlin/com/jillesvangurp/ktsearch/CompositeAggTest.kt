package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.CompositeAgg
import com.jillesvangurp.searchdsls.querydsl.agg
import com.jillesvangurp.searchdsls.querydsl.SortOrder
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class CompositeAggTest : SearchTestBase() {
    @Serializable
    data class Doc(
        val name: String,
        val color: String? = null,
        val value: Long? = null,
        val timestamp: String? = null,
        val location: Map<String, Double>? = null,
    ) {
        companion object {
            val mapping = IndexSettingsAndMappingsDSL().apply {
                mappings {
                    keyword(Doc::name)
                    keyword(Doc::color)
                    number<Long>(Doc::value)
                    date(Doc::timestamp)
                    geoPoint(Doc::location)
                }
            }
        }
    }

    private val compositeRepo = client.repository(randomIndexName(), Doc.serializer())

    private suspend fun ensureData() {
        runCatching { client.deleteIndex(compositeRepo.indexNameOrWriteAlias, ignoreUnavailable = true) }
        compositeRepo.createIndex(compositeRepo.indexNameOrWriteAlias, Doc.mapping)
        val now = Clock.System.now()
        compositeRepo.bulk {
            index(
                Doc(
                    name = "a",
                    color = "red",
                    value = 5,
                    timestamp = now.toString(),
                    location = mapOf("lat" to 52.0, "lon" to 4.0)
                )
            )
            index(
                Doc(
                    name = "b",
                    color = "green",
                    value = 15,
                    timestamp = (now - 1.days).toString(),
                    location = mapOf("lat" to 52.1, "lon" to 4.1)
                )
            )
            index(
                Doc(
                    name = "c",
                    color = "red",
                    value = 25,
                    timestamp = (now - 2.days).toString(),
                    location = mapOf("lat" to 48.8, "lon" to 2.3)
                )
            )
            // missing color/value to exercise missing_bucket
            index(
                Doc(
                    name = "d",
                    timestamp = (now - 3.days).toString(),
                    location = mapOf("lat" to 40.7, "lon" to -74.0)
                )
            )
        }
    }

    @Test
    fun shouldPaginateTermsSourceWithMissingBucket() = coRun {
        ensureData()
        val colors = mutableSetOf<String>()
        var sawMissing = false
        var afterKey: JsonObject? = null
        while (true) {
            val response = compositeRepo.search {
                resultSize = 0
                agg("by_color", CompositeAgg {
                    aggSize = 1
                    afterKey?.let { afterKey(it.toMap()) }
                    termsSource("color", Doc::color, missingBucket = true, order = SortOrder.ASC)
                })
            }
            val composite = response.aggregations.compositeResult("by_color")
            composite.parsedBuckets.forEach { b ->
                val colorKey = b.parsed.key["color"]
                if (colorKey == null || colorKey is JsonNull) {
                    sawMissing = true
                } else {
                    colors += colorKey.jsonPrimitive.content
                }
            }
            val nextAfter = composite.afterKey
            if (nextAfter == null || nextAfter == afterKey) {
                break
            }
            afterKey = nextAfter
        }

        colors shouldContainAll listOf("green", "red")
        sawMissing shouldBe true
    }

    @Test
    fun shouldSupportMultipleSources() = coRun {
        ensureData()
        val response = compositeRepo.search {
            resultSize = 0
            agg("color_value", CompositeAgg {
                aggSize = 10
                termsSource("color", Doc::color)
                histogramSource("value_bucket", Doc::value, interval = 10)
            })
        }

        val buckets = response.aggregations.compositeResult("color_value").parsedBuckets.map { it.parsed }
        buckets.isEmpty() shouldBe false
        // Expect combinations: red in two value buckets (0-9 and 20-29), green in 10-19, missing color not included due to missing bucket default
        val keys = buckets.map { bucket ->
            val color = bucket.key["color"]?.jsonPrimitive?.content
            val valueBucket = bucket.key["value_bucket"]?.jsonPrimitive?.doubleOrNull?.toLong()
            color to valueBucket
        }.toSet()

        keys shouldContainAll setOf(
            "red" to 0L,
            "red" to 20L,
            "green" to 10L
        )
    }

    @Test
    fun shouldHandleDateHistogramSource() = coRun {
        ensureData()
        val response = compositeRepo.search {
            resultSize = 0
            agg("by_day", CompositeAgg {
                aggSize = 10
                dateHistogramSource(
                    name = "day",
                    field = Doc::timestamp,
                    calendarInterval = "1d",
                    order = SortOrder.DESC
                )
            })
        }

        val buckets = response.aggregations.compositeResult("by_day").parsedBuckets
        buckets.shouldHaveSize(4)
        // newest bucket should be the current day first because of DESC order
        val firstKey = buckets.first().parsed.key["day"]?.jsonPrimitive?.content
        firstKey shouldNotBe null
    }

    @Test
    fun shouldHandleGeoTileGridSource() = coRun {
        ensureData()
        val response = compositeRepo.search {
            resultSize = 0
            agg("by_tile", CompositeAgg {
                aggSize = 10
                geoTileGridSource("tile", Doc::location, precision = 4)
            })
        }

        val buckets = response.aggregations.compositeResult("by_tile").parsedBuckets
        buckets.isEmpty() shouldBe false
    }

}
