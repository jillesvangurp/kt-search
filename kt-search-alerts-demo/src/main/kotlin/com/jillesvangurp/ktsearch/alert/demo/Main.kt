package com.jillesvangurp.ktsearch.alert.demo

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.alert.core.AlertService
import com.jillesvangurp.ktsearch.alert.notifications.ConsoleLevel
import com.jillesvangurp.ktsearch.alert.notifications.SendGridConfig
import com.jillesvangurp.ktsearch.alert.notifications.SendGridEmailSender
import com.jillesvangurp.ktsearch.alert.notifications.consoleNotification
import com.jillesvangurp.ktsearch.alert.notifications.emailNotification
import com.jillesvangurp.ktsearch.alert.notifications.slackNotification
import com.jillesvangurp.ktsearch.alert.notifications.SlackWebhookSender
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.RuleNotificationInvocation
import com.jillesvangurp.searchdsls.querydsl.match
import io.ktor.client.HttpClient
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
    val debug = env("DEBUG", "false").toBoolean()
    val slackHook = env("SLACK_HOOK", "").takeIf { it.isNotBlank() }
    val sendgrid = env("SENDGRID", "").takeIf { it.isNotBlank() }

    val client = SearchClient(
        KtorRestClient(
            host = elasticHost,
            port = elasticPort,
            logging = debug
        )
    )

    val httpClient = lazy { HttpClient() }

    val alerts = AlertService(client)

    alerts.start {
        notifications {
            addNotification(
                consoleNotification(
                    id = "console-alerts",
                    level = ConsoleLevel.INFO,
                    message = """{{ruleName}} matched {{matchCount}} documents in env:$environment at {{timestamp}}."""
                )
            )
            slackHook?.let { hook ->
                addNotification(
                    slackNotification(
                        id = "slack-alerts",
                        sender = SlackWebhookSender(httpClient.value),
                        webhookUrl = hook,
                        message = "Alert {{ruleName}} found {{matchCount}} documents"
                    )
                )
            }
            sendgrid?.let { key ->
                addNotification(
                    emailNotification(
                        id = "email-alerts",
                        sender = SendGridEmailSender(
                            httpClient = httpClient.value,
                            config = SendGridConfig(apiKey = key)
                        ),
                        from = "alerts@domain.com",
                        to = listOf("dude@domain.com"),
                        subject = "ALERT",
                        body = """
                            |Yo Dude,
                            |
                            |{{ruleName}} matched {{matchCount}} documents in env:$environment at {{timestamp}}.
                            |
                            |
                            |Kindly,
                            |
                            |Alerter
                        """.trimMargin()
                    )
                )
            }
        }

        rule(
            AlertRuleDefinition.newRule(
                id = "error-alert",
                name = "Error monitor",
                cronExpression = "*/1 * * * *",
                target = alertTarget,
                notifications = emptyList(),
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
        if (httpClient.isInitialized()) {
            httpClient.value.close()
        }
    }
}
