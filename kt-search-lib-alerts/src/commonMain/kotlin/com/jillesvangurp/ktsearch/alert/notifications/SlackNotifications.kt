package com.jillesvangurp.ktsearch.alert.notifications

import com.jillesvangurp.ktsearch.alert.core.retrySuspend
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val slackLogger = KotlinLogging.logger {}

data class SlackNotificationConfig(
    val webhookUrl: String,
    val channelName: String? = null,
    val username: String? = null,
    val message: String
)

data class SlackMessage(
    val text: String,
    val channel: String?,
    val username: String?
)

interface SlackSender {
    suspend fun send(config: SlackNotificationConfig, message: SlackMessage)
}

fun slackNotification(
    id: String,
    sender: SlackSender,
    webhookUrl: String,
    message: String,
    channelName: String? = null,
    username: String? = null,
    defaultVariables: Map<String, String> = emptyMap(),
    retryAttempts: Int = 3,
    retryDelay: Duration = 2.seconds
): NotificationDefinition {
    require(webhookUrl.isNotBlank()) { "Slack webhookUrl must be set for notification '$id'" }
    val messageTemplate = message.ifBlank { error("Slack message must be set for notification '$id'") }
    val config = SlackNotificationConfig(
        webhookUrl = webhookUrl,
        channelName = channelName,
        username = username,
        message = messageTemplate
    )
    return notification(
        id = id,
        defaultVariables = defaultVariables
    ) {
        val payload = SlackMessage(
            text = render(config.message),
            channel = config.channelName,
            username = config.username
        )
        retrySuspend(
            description = "slack-$id",
            maxAttempts = retryAttempts,
            initialDelay = retryDelay
        ) {
            sender.send(config, payload)
        }
        slackLogger.debug { "Slack notification '$id' sent for rule ${context.ruleId}" }
    }
}

class SlackWebhookSender(
    private val httpClient: HttpClient
) : SlackSender {
    override suspend fun send(config: SlackNotificationConfig, message: SlackMessage) {
        val payload = buildJsonObject {
            put("text", message.text)
            message.channel?.let { put("channel", it) }
            message.username?.let { put("username", it) }
        }.toString()
        val response = httpClient.post(config.webhookUrl) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrElse { "" }
            throw SlackWebhookException(response, body)
        }
    }
}

class SlackWebhookException(
    val response: HttpResponse,
    val body: String
) : Exception("Slack webhook error ${response.status.value}: $body")
