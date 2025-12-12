package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AggregationDslTest {
    @Test
    fun `should render terms aggregation options`() {
        val agg = TermsAgg("color") {
            aggSize = 10
            missing = "(missing)"
            executionHint = "map"
            collectMode = "breadth_first"
            shardMinDocCount = 2
            valueType = "string"
            orderByKey(SortOrder.ASC)
        }

        val termsConfig = agg[agg.name] as JsonDsl

        termsConfig shouldBe mapOf(
            "field" to "color",
            "size" to 10L,
            "missing" to "(missing)",
            "execution_hint" to "map",
            "collect_mode" to "breadth_first",
            "shard_min_doc_count" to 2L,
            "value_type" to "string",
            "order" to listOf(mapOf("_key" to "asc")),
        )
    }

    @Test
    fun `should render date histogram options`() {
        val agg = DateHistogramAgg("timestamp") {
            calendarInterval = "1d"
            missing = 0
            keyed = true
            orderByCount(SortOrder.DESC)
            extendedBounds(min = "now-7d", max = "now+1d")
            hardBounds(min = "now-30d", max = "now")
        }

        val dateHistogramConfig = agg[agg.name] as JsonDsl

        dateHistogramConfig shouldBe mapOf(
            "field" to "timestamp",
            "calendar_interval" to "1d",
            "missing" to 0,
            "keyed" to true,
            "order" to mapOf("_count" to "desc"),
            "extended_bounds" to mapOf("min" to "now-7d", "max" to "now+1d"),
            "hard_bounds" to mapOf("min" to "now-30d", "max" to "now"),
        )
    }
}
