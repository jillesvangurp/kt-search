package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.ktsearch.ClusterStatus
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
        val definition = rules.first() as AlertRuleDefinition.Search
        definition.notifications.shouldHaveSize(3)
        definition.notifications.map { it.notificationId } shouldContainExactly listOf(
            "email-critical",
            "slack-default",
            "console-log"
        )
        definition.notifications.last().variables["level"] shouldBe "warn"
        definition.notifications.first().variables["threshold"] shouldBe "90"
        definition.searchCheck.target shouldBe "logs-*"
        definition.searchCheck.queryJson.contains("match_all") shouldBe true
    }

    @Test
    fun `should capture firing condition`() {
        val definition = AlertRuleDefinition.newRule(
            name = "Conditioned",
            cronExpression = "* * * * *",
            target = "logs-*",
            notifications = emptyList(),
            firingCondition = RuleFiringCondition.AtMost(5)
        ) {
            query = matchAll()
        }

        definition.firingCondition shouldBe RuleFiringCondition.AtMost(5)
    }

    @Test
    fun `should build cluster status rule`() {
        val definition = AlertRuleDefinition.clusterStatusRule(
            name = "Cluster Green",
            cronExpression = "*/5 * * * *",
            notifications = emptyList(),
            expectedStatus = ClusterStatus.Green,
            description = "prod-cluster"
        )

        definition shouldBe AlertRuleDefinition.ClusterStatusRule(
            id = null,
            name = "Cluster Green",
            enabled = true,
            cronExpression = "*/5 * * * *",
            target = "prod-cluster",
            message = null,
            failureMessage = null,
            notifications = emptyList(),
            failureNotifications = emptyList(),
            repeatNotificationIntervalMillis = null,
            startImmediately = true,
            clusterCheck = RuleCheck.ClusterStatusCheck(
                expectedStatus = ClusterStatus.Green,
                description = "prod-cluster"
            )
        )
        definition.clusterCheck shouldBe RuleCheck.ClusterStatusCheck(
            expectedStatus = ClusterStatus.Green,
            description = "prod-cluster"
        )
    }
}
