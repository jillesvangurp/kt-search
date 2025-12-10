package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import kotlin.test.Test
import kotlin.test.assertEquals

class PercentilesAggTest {
    @Test
    fun `should default to keyed percentiles`() {
        val agg = PercentilesAgg("load_time") {
            percentileValues = listOf(50.0, 95.0, 99.0)
        }

        val config = agg["percentiles"] as JsonDsl
        assertEquals(true, config["keyed"])
        assertEquals(listOf(50.0, 95.0, 99.0), config["values"])
    }

    @Test
    fun `should apply custom tdigest compression`() {
        val agg = PercentileRanksAgg("load_time") {
            rankValues = listOf(42.0, 100.0)
            tdigest(compression = 120.0)
        }

        val config = agg["percentile_ranks"] as JsonDsl
        val tdigest = config["tdigest"] as JsonDsl
        assertEquals(listOf(42.0, 100.0), config["values"])
        assertEquals(120.0, tdigest["compression"])
    }
}
