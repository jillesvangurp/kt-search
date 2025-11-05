package com.jillesvangurp.ktsearch.alert.rules

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RuleFiringConditionTest {
    @Test
    fun `default triggers when matches exist`() {
        RuleFiringCondition.Default.shouldTrigger(0) shouldBe false
        RuleFiringCondition.Default.shouldTrigger(1) shouldBe true
    }

    @Test
    fun `at least triggers when below threshold`() {
        val condition = RuleFiringCondition.AtLeast(3)
        condition.shouldTrigger(3) shouldBe false
        condition.shouldTrigger(2) shouldBe true
    }

    @Test
    fun `max triggers when exceeding threshold`() {
        val condition = RuleFiringCondition.Max(2)
        condition.shouldTrigger(2) shouldBe false
        condition.shouldTrigger(3) shouldBe true
    }
}
