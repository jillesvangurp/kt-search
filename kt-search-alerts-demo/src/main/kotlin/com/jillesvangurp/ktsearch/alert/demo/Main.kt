package com.jillesvangurp.ktsearch.alert.demo

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.alert.core.AlertService
import com.jillesvangurp.ktsearch.alert.notifications.ConsoleLevel
import com.jillesvangurp.ktsearch.alert.notifications.SendGridConfig
import com.jillesvangurp.ktsearch.alert.notifications.SendGridEmailSender
import com.jillesvangurp.ktsearch.alert.notifications.consoleNotification
import com.jillesvangurp.ktsearch.alert.notifications.emailNotification
import com.jillesvangurp.ktsearch.alert.notifications.slackNotification
import com.jillesvangurp.ktsearch.alert.notifications.SlackWebhookSender
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.RuleFiringCondition
import com.jillesvangurp.searchdsls.querydsl.match
import io.ktor.client.HttpClient
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.awaitCancellation

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
    val emailFrom = env("EMAIL_FROM", "alerts@domain.com")
    val notificationEmails = env("EMAIL_TO", "dude@domain.com")
        .split(',').map { it.trim() }.filter { it.isNotBlank() }

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

            +consoleNotification(
                id = "console-alerts",
                level = ConsoleLevel.ERROR,
                message = """
                    |{{ruleMessage}} ({{resultDescription}}) in env:$environment at {{timestamp}}.
                    |
                    |{{matchesJson}}
                """.trimMargin()
            )
            slackHook?.let { hook ->
                +slackNotification(
                    id = "slack-alerts",
                    sender = SlackWebhookSender(httpClient.value),
                    webhookUrl = hook,
                    message = "*{{ruleMessage}}* ({{resultDescription}}).\n\n```json\n{{matchesJson}}\n```"
                )
            }
            sendgrid?.let { key ->
                +emailNotification(
                    id = "email-alerts",
                    sender = SendGridEmailSender(
                        httpClient = httpClient.value,
                        config = SendGridConfig(apiKey = key)
                    ),
                    from = emailFrom,
                    to = notificationEmails,
                    subject = "ALERT",
                    body = """
                            |Yo Dude,
                            |
                            |{{ruleMessage}} 
                            |
                            |{{resultDescription}} in env:$environment at {{timestamp}}:
                            |
                            |{{matchesJson}}
                            |
                            |
                            |Kindly,
                            |
                            |Alerter
                        """.trimMargin()
                )
            }
        }
        notificationDefaults {
            notifyOnFailures = true
            repeatNotificationsEvery = 10.minutes
        }
        rules {

            +AlertRuleDefinition.newRule(
                id = "error-alert",
                name = "Error Rate Monitor",
                cronExpression = "*/1 * * * *",
                target = alertTarget,
                message = "Too many ObjectMarker errors in env:$environment",
                failureMessage = "Failed to check ObjectMarker errors in env:$environment",
                notifications = emptyList(),
                startImmediately = true,
                firingCondition = RuleFiringCondition.AtMost(25)
            ) {
                resultSize = 2
                match("objectType", "ObjectMarker")
            }

//            +AlertRuleDefinition.newRule(
//                id = "missing-errors-alert",
//                name = "Error Pipeline Watchdog",
//                cronExpression = "*/2 * * * *",
//                target = alertTarget,
//                message = "Expected ObjectMarker errors missing in env:$environment",
//                failureMessage = "Failed to verify ObjectMarker errors in env:$environment",
//                notifications = emptyList(),
//                startImmediately = true,
//                firingCondition = RuleFiringCondition.AtLeast(1)
//            ) {
//                resultSize = 2
//                match("objectType", "ObjectMarker")
//            }

            +AlertRuleDefinition.clusterStatusRule(
                id = "cluster-health",
                name = "Cluster Health",
                cronExpression = "*/5 * * * *",
                expectedStatus = ClusterStatus.Green,
                description = "$environment-cluster",
                message = "Cluster status degraded in env:$environment",
                failureMessage = "Failed to verify cluster status in env:$environment",
                notifications = emptyList(),
                startImmediately = true
            )
        }
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
