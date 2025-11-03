package com.jillesvangurp.ktsearch.alert.demo

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.alert.core.AlertService
import com.jillesvangurp.ktsearch.alert.notifications.ConsoleLevel
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDispatcherConfig
import com.jillesvangurp.ktsearch.alert.notifications.createNotificationDispatcher
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.RuleNotificationInvocation
import com.jillesvangurp.searchdsls.querydsl.match
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

private fun env(key: String, default: String) = System.getenv(key) ?: default

@OptIn(ExperimentalTime::class)
suspend fun main() {
    val elasticHost = env("ELASTIC_HOST", "localhost")
    val elasticPort = env("ELASTIC_PORT", "9999").toInt()
    val alertTarget = env("ALERT_TARGET", "formation-objects")
    val environment = env("ENVIRONMENT", "prod")
    val slackHook = env("SLACK_HOOK", "").takeIf { it.isNotBlank() }
    val sendgrid = env("SLACK_HOOK", "").takeIf { it.isNotBlank() }

    val client = SearchClient(
        KtorRestClient(
            host = elasticHost,
            port = elasticPort,
            logging = false
        )
    )

    val dispatcher = createNotificationDispatcher(
        config = NotificationDispatcherConfig(includeConsole = true)
    )

    val alerts = AlertService(client, dispatcher)

    alerts.start {
        notifications(
            NotificationDefinition.console(
                id = "console-alerts",
                level = ConsoleLevel.INFO,
                message = """
                    |Yo Dude,
                    |
                    |{{ruleName}} matched {{matchCount}} documents in env:${environment} at {{timestamp}}.
                    |
                    |
                    |Kindly,
                    |
                    |Alerter
                    """.trimMargin()
            )
        )

        rule(
            AlertRuleDefinition.newRule(
                id = "error-alert",
                name = "Error monitor",
                cronExpression = "*/1 * * * *",
                target = alertTarget,
                notifications = RuleNotificationInvocation.many("console-alerts"),
                startImmediately = true
            ) {
                match("objectType", "ObjectMarker")
            }
        )
    }

    println("Alert service running against $alertTarget on $elasticHost:$elasticPort. Press Ctrl+C to stop.")

    try {
        awaitCancellation()
    } finally {
        alerts.stop()
        client.close()
    }
}
