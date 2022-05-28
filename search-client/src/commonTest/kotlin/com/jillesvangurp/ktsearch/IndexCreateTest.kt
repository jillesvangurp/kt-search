package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IndexCreateTest: SearchTestBase() {
    @Test
    fun createIndex() = coTest {
        val response = client.createIndex(randomIndexName()) {
            mappings(false) {
                keyword("foo")
                number<Long>("bar")
            }
            meta {
                this["foo"] = "bar"
            }
            settings {
                replicas=0
                shards=5
            }
        }
        response.acknowledged shouldBe true
    }
}