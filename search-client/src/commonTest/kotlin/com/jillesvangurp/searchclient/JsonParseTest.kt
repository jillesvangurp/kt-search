package com.jillesvangurp.searchclient

import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test


class JsonParseTest {
    @Serializable
    data class Bar(val bar: Int)

    @Test
    fun shouldParse() {
        val input="""
            {
                "foo": {
                    "bar": 42
                }
            }
        """.trimIndent()
        val parsed = Json.parseToJsonElement(input).jsonObject
        val bar = Json.decodeFromJsonElement<Bar>(parsed["foo"]?.jsonObject!!)
        bar.bar shouldBe 42
    }
}