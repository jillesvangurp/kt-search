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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

private fun env(key: String, default: String) = System.getenv(key) ?: default

@OptIn(kotlin.time.ExperimentalTime::class)
fun main(): Unit = runBlocking {
    val elasticHost = env("ELASTIC_HOST", "localhost")
    val elasticPort = env("ELASTIC_PORT", "9200").toInt()
    val alertTarget = env("ALERT_TARGET", "logs-*")
    val environment = env("ENVIRONMENT", "prod")

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
                message = "[{{timestamp}}] {{ruleName}} matched {{matchCount}} documents in $environment"
            )
        )

        rule(
            AlertRuleDefinition.newRule(
                id = "error-alert",
                name = "Error monitor",
                cronExpression = "*/5 * * * *",
                target = alertTarget,
                notifications = RuleNotificationInvocation.many("console-alerts"),
                startImmediately = true
            ) {
                match("level", "error")
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
