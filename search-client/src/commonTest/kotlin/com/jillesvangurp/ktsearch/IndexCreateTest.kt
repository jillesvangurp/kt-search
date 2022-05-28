package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IndexCreateTest: SearchTestBase() {
    @Test
    fun createIndex() = runTest {
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