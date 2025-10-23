package com.jillesvangurp.ktsearch.alert

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

interface NotificationPlugin {
    suspend fun send(message: AlertNotification, context: AlertSendContext)
}

data class AlertSendContext(
    val rule: AlertRule,
    val matches: List<JsonObject>,
    val triggeredAt: Instant
)

class SendGridNotificationPlugin(
    private val httpClient: HttpClient,
    private val config: SendGridConfig
) : NotificationPlugin {
    override suspend fun send(message: AlertNotification, context: AlertSendContext) {
        retrySuspend(
            description = "sendgrid",
            maxAttempts = config.maxRetries,
            initialDelay = config.retryDelay,
            shouldRetry = { throwable -> throwable is RetryableSendException }
        ) {
            val response = httpClient.post(config.endpoint) {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(buildPayload(message))
            }
            handleResponse(response)
        }
        logger.debug { "SendGrid alert sent for rule ${context.rule.id} to ${message.to}" }
    }

    private fun buildPayload(alertNotification: AlertNotification) = buildJsonObject {
        putJsonArray("personalizations") {
            add(buildJsonObject {
                putJsonArray("to") {
                    alertNotification.to.forEach { address ->
                        add(buildJsonObject { put("email", address) })
                    }
                }
                if (alertNotification.cc.isNotEmpty()) {
                    putJsonArray("cc") {
                        alertNotification.cc.forEach { address ->
                            add(buildJsonObject { put("email", address) })
                        }
                    }
                }
                if (alertNotification.bcc.isNotEmpty()) {
                    putJsonArray("bcc") {
                        alertNotification.bcc.forEach { address ->
                            add(buildJsonObject { put("email", address) })
                        }
                    }
                }
            })
        }
        putJsonObject("from") {
            put("email", alertNotification.from)
        }
        put("subject", alertNotification.subject)
        putJsonArray("content") {
            add(buildJsonObject {
                put("type", alertNotification.contentType)
                put("value", alertNotification.body)
            })
        }
    }

    private suspend fun handleResponse(response: HttpResponse) {
        if (response.status.isSuccess()) {
            return
        }
        val body = runCatching { response.bodyAsText() }.getOrElse { "" }
        val statusCode = response.status.value
        val exception = SendGridException(statusCode, body)
        if (statusCode == 429 || statusCode >= 500) {
            throw RetryableSendException(exception)
        } else {
            throw exception
        }
    }
}

@Serializable
data class SendGridConfig(
    val apiKey: String,
    val endpoint: String = "https://api.sendgrid.com/v3/mail/send",
    val maxRetries: Int = 4,
    val retryDelay: Duration = 2.seconds
)

open class SendGridException(val statusCode: Int, body: String) : Exception("SendGrid error $statusCode: $body")

class RetryableSendException(cause: SendGridException) : Exception(cause)
