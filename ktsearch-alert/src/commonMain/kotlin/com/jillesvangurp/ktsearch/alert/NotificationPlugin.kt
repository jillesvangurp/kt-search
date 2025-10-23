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
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

interface AlertSendPlugin {
    suspend fun send(renderedEmail: RenderedEmail, context: AlertSendContext)
}

data class AlertSendContext(
    val rule: AlertRule,
    val matches: List<JsonObject>,
    val triggeredAt: Instant
)

class SendGridAlertPlugin(
    private val httpClient: HttpClient,
    private val config: SendGridConfig
) : AlertSendPlugin {
    override suspend fun send(renderedEmail: RenderedEmail, context: AlertSendContext) {
        retrySuspend(
            description = "sendgrid",
            maxAttempts = config.maxRetries,
            initialDelay = config.retryDelay,
            shouldRetry = { throwable -> throwable is RetryableSendException }
        ) {
            val response = httpClient.post(config.endpoint) {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(buildPayload(renderedEmail))
            }
            handleResponse(response)
        }
        logger.debug { "SendGrid alert sent for rule ${context.rule.id} to ${renderedEmail.to}" }
    }

    private fun buildPayload(renderedEmail: RenderedEmail) = buildJsonObject {
        putJsonArray("personalizations") {
            add(buildJsonObject {
                putJsonArray("to") {
                    renderedEmail.to.forEach { address ->
                        add(buildJsonObject { put("email", address) })
                    }
                }
                if (renderedEmail.cc.isNotEmpty()) {
                    putJsonArray("cc") {
                        renderedEmail.cc.forEach { address ->
                            add(buildJsonObject { put("email", address) })
                        }
                    }
                }
                if (renderedEmail.bcc.isNotEmpty()) {
                    putJsonArray("bcc") {
                        renderedEmail.bcc.forEach { address ->
                            add(buildJsonObject { put("email", address) })
                        }
                    }
                }
            })
        }
        putJsonObject("from") {
            put("email", renderedEmail.from)
        }
        put("subject", renderedEmail.subject)
        putJsonArray("content") {
            add(buildJsonObject {
                put("type", renderedEmail.contentType)
                put("value", renderedEmail.body)
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
