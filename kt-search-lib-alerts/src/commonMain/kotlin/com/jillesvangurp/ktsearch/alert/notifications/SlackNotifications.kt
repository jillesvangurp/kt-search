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
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val slackLogger = KotlinLogging.logger {}

data class SlackMessage(
    val text: String,
    val channel: String?,
    val username: String?
)

interface SlackSender {
    suspend fun send(config: SlackNotificationConfig, message: SlackMessage)
}

class SlackNotificationHandler(
    private val sender: SlackSender,
    private val maxRetries: Int = 3,
    private val retryDelay: Duration = 2.seconds
) : NotificationHandler {
    override val channel: NotificationChannel = NotificationChannel.SLACK

    override suspend fun send(
        definition: NotificationDefinition,
        variables: Map<String, String>,
        context: NotificationContext
    ) {
        val config = definition.config as? SlackNotificationConfig
            ?: error("Notification '${definition.id}' is not configured as Slack")
        val message = SlackMessage(
            text = renderTemplate(config.message, variables),
            channel = config.channelName,
            username = config.username
        )
        retrySuspend(
            description = "slack-${definition.id}",
            maxAttempts = maxRetries,
            initialDelay = retryDelay
        ) {
            sender.send(config, message)
        }
        slackLogger.debug { "Slack notification '${definition.id}' sent for rule ${context.ruleId}" }
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
            setBody(TextContent(payload, ContentType.Application.Json))
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
