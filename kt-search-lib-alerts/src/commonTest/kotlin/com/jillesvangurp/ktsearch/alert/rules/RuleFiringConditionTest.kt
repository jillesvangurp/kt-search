package com.jillesvangurp.ktsearch.alert.rules

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class RuleFiringConditionTest {
    @Test
    fun `greater than default triggers when matches exist`() {
        RuleFiringCondition.GreaterThan(0).shouldTrigger(0) shouldBe false
        RuleFiringCondition.GreaterThan(0).shouldTrigger(1) shouldBe true
    }

    @Test
    fun `equals only triggers on exact match`() {
        val condition = RuleFiringCondition.Equals(2)
        condition.shouldTrigger(2) shouldBe true
        condition.shouldTrigger(3) shouldBe false
    }

    @Test
    fun `less than or equal triggers when below threshold`() {
        val condition = RuleFiringCondition.LessThanOrEqual(1)
        condition.shouldTrigger(1) shouldBe true
        condition.shouldTrigger(2) shouldBe false
    }

    @Test
    fun `legacy conditions still follow previous semantics`() {
        RuleFiringCondition.AtMost(2).shouldTrigger(3) shouldBe true
        RuleFiringCondition.AtLeast(3).shouldTrigger(2) shouldBe true
    }
}
