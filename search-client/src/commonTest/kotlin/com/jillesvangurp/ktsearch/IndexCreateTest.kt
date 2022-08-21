package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test

class IndexCreateTest: SearchTestBase() {
    @Test
    fun createIndex() = runTest {
        val index = randomIndexName()
        val response = client.createIndex(index) {
            dynamicTemplate("test_fields") {
                match = "test*"
                mapping("text") {
                    fields {
                        keyword("keyword")
                    }
                }
            }
            dynamicTemplate("more_fields") {
                match = "more*"
                mapping("keyword")
            }
            mappings(true) {
                keyword("foo")
                number<Long>("bar")
                objField("foo", dynamic = "true")
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
        client.getIndexMappings(index).let {
            it[index]?.jsonObject?.get("mappings")?.jsonObject?.get("dynamic_templates")?.jsonArray?.size shouldBe 2
        }
    }
}