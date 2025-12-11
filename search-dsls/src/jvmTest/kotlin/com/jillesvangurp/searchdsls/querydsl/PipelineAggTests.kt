package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.withJsonDsl
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineAggTests {
    @Test
    fun bucketSelectorSerializes() {
        val search = SearchDSL().apply {
            agg("by_color", TermsAgg("color")) {
                agg("total_sales", SumAgg("price"))
                agg("high_volume", BucketSelectorAgg {
                    bucketsPath = BucketsPath {
                        this["sales"] = "total_sales"
                    }
                    script = "params.sales > 50"
                    gapPolicy = "insert_zeros"
                })
            }
        }

        val byColor = search.getAggs()["by_color"] as AggQuery
        val byColorAggs = byColor["aggs"] as JsonDsl
        val totalSales = byColorAggs["total_sales"] as AggQuery
        val selector = byColorAggs["high_volume"] as AggQuery
        val selectorConfig = selector["bucket_selector"] as JsonDsl

        assertEquals("color", (byColor["terms"] as JsonDsl)["field"])
        assertEquals("price", (totalSales["sum"] as JsonDsl)["field"])
        assertEquals("total_sales", (selectorConfig["buckets_path"] as BucketsPath)["sales"])
        assertEquals("params.sales > 50", selectorConfig["script"])
        assertEquals("insert_zeros", selectorConfig["gap_policy"])
    }

    @Test
    fun derivativeAndCumulativeSumSerialize() {
        val search = SearchDSL().apply {
            agg("sales_over_time", DateHistogramAgg("timestamp") { calendarInterval = "1d" }) {
                agg("daily_sales", SumAgg("price"))
                agg("sales_derivative", DerivativeAgg {
                    bucketsPath = "daily_sales"
                    gapPolicy = "skip"
                })
                agg("running_total", CumulativeSumAgg {
                    bucketsPath = "daily_sales"
                })
            }
        }

        val overTime = search.getAggs()["sales_over_time"] as AggQuery
        val overTimeAggs = overTime["aggs"] as JsonDsl
        val dailySales = overTimeAggs["daily_sales"] as AggQuery
        val derivative = overTimeAggs["sales_derivative"] as AggQuery
        val derivativeConfig = derivative["derivative"] as JsonDsl
        val cumulative = overTimeAggs["running_total"] as AggQuery
        val cumulativeConfig = cumulative["cumulative_sum"] as JsonDsl

        assertEquals("timestamp", (overTime["date_histogram"] as JsonDsl)["field"])
        assertEquals("1d", (overTime["date_histogram"] as JsonDsl)["calendar_interval"])
        assertEquals("price", (dailySales["sum"] as JsonDsl)["field"])
        assertEquals("daily_sales", derivativeConfig["buckets_path"])
        assertEquals("skip", derivativeConfig["gap_policy"])
        assertEquals("daily_sales", cumulativeConfig["buckets_path"])
    }
}

private fun SearchDSL.getAggs(): JsonDsl {
    return this["aggs"] as JsonDsl
}
