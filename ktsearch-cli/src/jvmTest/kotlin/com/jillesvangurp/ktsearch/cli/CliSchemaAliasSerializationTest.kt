package com.jillesvangurp.ktsearch.cli

import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class CliSchemaAliasSerializationTest {
    @Test
    fun composeSchemaJsonSanitizesImmutableSettings() {
        val settingsRaw = """
            {
              "products": {
                "settings": {
                  "index": {
                    "number_of_shards": "1",
                    "routing_partition_size": "2",
                    "provided_name": "products",
                    "creation_date": "1",
                    "uuid": "abc"
                  }
                }
              }
            }
        """.trimIndent()
        val mappingsRaw = """
            {
              "products": {
                "mappings": {
                  "properties": {
                    "title": {
                      "type": "keyword"
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val actual = Json.parseToJsonElement(
            composeSchemaJson("products", settingsRaw, mappingsRaw),
        ).jsonObject

        val indexSettings = actual["settings"]!!.jsonObject["index"]!!.jsonObject
        indexSettings["number_of_shards"]?.toString() shouldBe "\"1\""
        indexSettings["routing_partition_size"]?.toString() shouldBe "\"2\""
        indexSettings["provided_name"] shouldBe null
        indexSettings["creation_date"] shouldBe null
        indexSettings["uuid"] shouldBe null
        actual["mappings"]!!.jsonObject["properties"] shouldBe
            Json.parseToJsonElement("""{"title":{"type":"keyword"}}""")
    }

    @Test
    fun composeAliasActionsPreservesAliasProperties() {
        val aliasesRaw = """
            {
              "products": {
                "aliases": {
                  "products-read": {
                    "is_write_index": false
                  },
                  "products-write": {
                    "is_write_index": true
                  }
                }
              }
            }
        """.trimIndent()

        val actions = Json.parseToJsonElement(
            composeAliasActions("products", aliasesRaw),
        ).jsonObject["actions"]!!.toString()

        actions.contains("\"alias\":\"products-read\"") shouldBe true
        actions.contains("\"alias\":\"products-write\"") shouldBe true
        actions.contains("\"is_write_index\":true") shouldBe true
    }
}
