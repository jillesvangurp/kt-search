package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.withJsonDsl
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.test.assertEquals

class HistogramAggTest {
    @Test
    fun `should serialize basic histogram agg`() {
        val searchDsl = SearchDSL().apply {
            agg("prices", HistogramAgg("price", interval = 5))
        }

        val expected = withJsonDsl {
            this["aggs"] = withJsonDsl {
                this["prices"] = withJsonDsl {
                    this["histogram"] = withJsonDsl {
                        this["field"] = "price"
                        this["interval"] = 5
                    }
                }
            }
        }

        expected.toString() shouldBe searchDsl.toString()
    }

    @Test
    fun `should serialize histogram bounds and missing`() {
        val searchDsl = SearchDSL().apply {
            agg("prices", HistogramAgg("price", interval = 10) {
                offset = 2
                minDocCount = 1
                missing = 0
                extendedBounds(min = 0, max = 50)
                hardBounds(min = 0, max = 60)
            })
        }

        val expected = withJsonDsl {
            this["aggs"] = withJsonDsl {
                this["prices"] = withJsonDsl {
                    this["histogram"] = withJsonDsl {
                        this["field"] = "price"
                        this["interval"] = 10
                        this["offset"] = 2
                        this["min_doc_count"] = 1L
                        this["missing"] = 0
                        this["extended_bounds"] = withJsonDsl {
                            this["min"] = 0
                            this["max"] = 50
                        }
                        this["hard_bounds"] = withJsonDsl {
                            this["min"] = 0
                            this["max"] = 60
                        }
                    }
                }
            }
        }

        expected.toString() shouldBe searchDsl.toString()
    }
}
