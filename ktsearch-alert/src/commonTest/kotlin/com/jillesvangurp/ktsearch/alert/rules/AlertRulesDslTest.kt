package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlertRulesDslTest {
    @Test
    fun `should build rules with notification references`() {
        val notifications = RuleNotificationInvocation.many(
            "email-critical",
            "slack-default",
            variables = mapOf("threshold" to "90")
        ).plus(
            RuleNotificationInvocation.withVariables("console-log", "level" to "warn")
        )

        val rules = listOf(
            AlertRuleDefinition.newRule(
                id = "rule-id",
                name = "Example",
                cronExpression = "* * * * *",
                target = "logs-*",
                notifications = notifications
            ) {
                query = matchAll()
            }
        )

        rules.shouldHaveSize(1)
        val definition = rules.first()
        definition.notifications.shouldHaveSize(3)
        definition.notifications.map { it.notificationId } shouldContainExactly listOf(
            "email-critical",
            "slack-default",
            "console-log"
        )
        definition.notifications.last().variables["level"] shouldBe "warn"
        definition.notifications.first().variables["threshold"] shouldBe "90"
    }
}
