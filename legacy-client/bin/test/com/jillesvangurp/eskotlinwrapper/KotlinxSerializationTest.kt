package com.jillesvangurp.eskotlinwrapper

import com.jillesvangurp.jsondsl.JsonDsl
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class KotlinxSerializationTest {
    @Test
    fun `should serialize and deserialize`() {
        val foo = Foo().apply {
            aString = "ohai"
            aNumber = 42
            aList = listOf("hello", "world")
        }
        val s = Json.encodeToString(foo.asJsonObject())
        println(s)
    }
}

class Foo : JsonDsl() {
    var aString: String by property()
    var aNumber: Int by property()
    var aList: List<String> by property()
}