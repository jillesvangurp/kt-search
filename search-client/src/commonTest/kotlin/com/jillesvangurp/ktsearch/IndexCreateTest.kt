package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class IndexCreateTest: SearchTestBase() {
    @Test
    fun createIndex() = coRun {
        val indexName = randomIndexName()
        val response = client.createIndex(indexName) {
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
                replicas = 0
                shards = 5
                refreshInterval = 31.seconds
            }
        }
        response.acknowledged shouldBe true
        client.getIndex(indexName).jsonObject(indexName).let {
            val mappings = it.jsonObject("mappings")
            mappings.jsonPrimitive("dynamic").booleanOrNull shouldBe true
            mappings.jsonObject("_meta").jsonPrimitive("foo").content shouldBe "bar"
            mappings.jsonArray("dynamic_templates").size shouldBe 2

            val settings = it.jsonObject("settings").jsonObject("index")
            settings.jsonPrimitive("number_of_replicas").intOrNull shouldBe 0
            settings.jsonPrimitive("number_of_shards").intOrNull shouldBe 5
            settings.jsonPrimitive("refresh_interval").content shouldBe "31s"
        }
    }

    private fun JsonObject.jsonPrimitive(key: String) = this.getValue(key).jsonPrimitive
    private fun JsonObject.jsonObject(key: String) = this.getValue(key).jsonObject
    private fun JsonObject.jsonArray(key: String) = this.getValue(key).jsonArray
}

