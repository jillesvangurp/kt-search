package com.jillesvangurp.ktsearch.alert.notifications

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val emailLogger = KotlinLogging.logger {}

data class EmailMessage(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String,
    val cc: List<String>,
    val bcc: List<String>
)

fun EmailNotificationConfig.render(variables: Map<String, String>): EmailMessage =
    EmailMessage(
        from = from,
        to = to,
        subject = renderTemplate(subject, variables),
        body = renderTemplate(body, variables),
        contentType = contentType,
        cc = cc,
        bcc = bcc
    )

interface EmailSender {
    suspend fun send(message: EmailMessage)
}

class EmailNotificationHandler(
    private val sender: EmailSender
) : NotificationHandler {
    override val channel: NotificationChannel = NotificationChannel.EMAIL

    override suspend fun send(
        definition: NotificationDefinition,
        variables: Map<String, String>,
        context: NotificationContext
    ) {
        val emailConfig = definition.config as? EmailNotificationConfig
            ?: error("Notification '${definition.id}' is not configured as an email notification")
        val rendered = emailConfig.render(variables)
        sender.send(rendered)
        emailLogger.warn { "Email notification '${definition.id}' sent for rule ${context.ruleId}:\n\n${rendered}" }
    }
}

class ConsoleNotificationHandler : NotificationHandler {
    override val channel: NotificationChannel = NotificationChannel.CONSOLE

    override suspend fun send(
        definition: NotificationDefinition,
        variables: Map<String, String>,
        context: NotificationContext
    ) {
        val config = definition.config as? ConsoleNotificationConfig
            ?: error("Notification '${definition.id}' is not configured as console")
        val message = renderTemplate(config.message, variables)
        when (config.level) {
            ConsoleLevel.TRACE -> emailLogger.trace { message }
            ConsoleLevel.DEBUG -> emailLogger.debug { message }
            ConsoleLevel.INFO -> emailLogger.info { message }
            ConsoleLevel.WARN -> emailLogger.warn { message }
            ConsoleLevel.ERROR -> emailLogger.error { message }
        }
    }
}

class SendGridEmailSender(
    private val httpClient: HttpClient,
    private val config: SendGridConfig
) : EmailSender {
    override suspend fun send(message: EmailMessage) {
        retrySend(message)
    }

    private suspend fun retrySend(message: EmailMessage) {
        com.jillesvangurp.ktsearch.alert.core.retrySuspend(
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
    }

    private fun buildPayload(alertNotification: EmailMessage) = buildJsonObject {
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
