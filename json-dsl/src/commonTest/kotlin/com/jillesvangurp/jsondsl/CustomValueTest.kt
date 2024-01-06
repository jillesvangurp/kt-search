package com.jillesvangurp.jsondsl

import io.kotest.matchers.shouldBe
import kotlin.test.Test

enum class GradeEnum(override val value: Int) : CustomValue<Int> {
    A(1),
    B(2)
}

class DslWithEnum : JsonDsl() {
    var grade by property<GradeEnum>()
}

class CustomValueTest {

    @Test
    fun enumSerialization() {
        val dsl = DslWithEnum().apply {
            grade = GradeEnum.A
        }

        dsl.json() shouldBe """{"grade":1}"""
    }
}