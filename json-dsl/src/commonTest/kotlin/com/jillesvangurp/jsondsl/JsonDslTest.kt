package com.jillesvangurp.jsondsl

import com.jillesvangurp.jsondsl.*
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class MyDsl:JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var foo by property<String>()
    // will be snake_cased in the json
    var meaningOfLife by property<Int>()
    // we override the property name here
    var l by property<List<Any>>("a_custom_list")
    var m by property<Map<Any,Any>>()
}

class JsonDslTest {

    @Test
    fun shouldSnakeCaseNames() {
        "fooBarFooBar".snakeCaseToUnderscore() shouldBe "foo_bar_foo_bar"
        "foo_BarFooBar".snakeCaseToUnderscore() shouldBe  "foo_bar_foo_bar"
        "foo1Bar1Foo1Bar".snakeCaseToUnderscore() shouldBe  "foo1_bar1_foo1_bar"
    }

    @Test
    fun shouldProduceValidJsonAndPropertyNameHandling() {
        // you may want to introduce some shorthand for this in your own dsls
        val myDsl = MyDsl().apply {
            foo = "Hello\tWorld"
            meaningOfLife = 42
            l = listOf("1", 2, 3)
            m = mapOf(42 to "fortytwo")
        }
        myDsl.json() shouldBe "{\"foo\":\"Hello\\tWorld\",\"meaning_of_life\":42,\"a_custom_list\":[\"1\",2,3],\"m\":{\"42\":\"fortytwo\"}}"
        myDsl.json(pretty = true) shouldBe
            """
            {
              "foo": "Hello\tWorld",
              "meaning_of_life": 42,
              "a_custom_list": [
                "1", 
                2, 
                3
              ],
              "m": {
                "42": "fortytwo"
              }
            }""".trimIndent()
    }
}