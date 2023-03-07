package com.jillesvangurp.jsondsl

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class MyDsl:JsonDsl() {
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
        "fooBarFooBar".camelCase2SnakeCase() shouldBe "foo_bar_foo_bar"
        "foo_BarFooBar".camelCase2SnakeCase() shouldBe  "foo_bar_foo_bar"
        "foo1Bar1Foo1Bar".camelCase2SnakeCase() shouldBe  "foo1_bar1_foo1_bar"
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

    @Test
    fun shouldIndentCorrectly() {
        withJsonDsl  {
            this["f"]= mapOf("f" to mapOf("f" to 1))
        }.json(true) shouldBe """
            {
              "f": {
                "f": {
                  "f": 1
                }
              }
            }
        """.trimIndent()

        withJsonDsl  {
            this["f1"] = 1
            this["f2"] = withJsonDsl  {
                this["f1"] = 1
            }
        }.json(true) shouldBe """
            {
              "f1": 1,
              "f2": {
                "f1": 1
              }
            }
        """.trimIndent()
    }

    @Test
    fun shouldNotSerializeNullValues() {
        withJsonDsl {
            this["foo"] = null
            this["bar"] = 42
        }.json(true) shouldNotContain "foo"
    }
}