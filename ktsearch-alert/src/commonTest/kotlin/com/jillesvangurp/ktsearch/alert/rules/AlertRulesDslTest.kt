package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AlertRulesDslTest {
    @Test
    fun `should build rules with notification references`() {
        val rules = alertRules {
            rule("rule-id") {
                name = "Example"
                target("logs-*")
                cron("* * * * *")
                query {
                    query = matchAll()
                }
                notifications("email-critical", "slack-default") {
                    variable("threshold", "90")
                }
                notification("console-log") {
                    variable("level", "warn")
                }
            }
        }

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
