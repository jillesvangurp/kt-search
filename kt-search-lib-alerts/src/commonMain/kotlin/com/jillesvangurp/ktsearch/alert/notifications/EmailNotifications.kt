package com.jillesvangurp.ktsearch.alert.notifications

import com.jillesvangurp.ktsearch.alert.core.retrySuspend
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

data class EmailNotificationConfig(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String = "text/plain",
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList()
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

fun emailNotification(
    id: String,
    sender: EmailSender,
    from: String,
    to: List<String>,
    subject: String,
    body: String,
    contentType: String = "text/plain",
    cc: List<String> = emptyList(),
    bcc: List<String> = emptyList(),
    defaultVariables: Map<String, String> = emptyMap()
): NotificationDefinition {
    require(from.isNotBlank()) { "From address must be set for email notification '$id'" }
    val toRecipients = to.normalizeAddresses()
    require(toRecipients.isNotEmpty()) { "At least one recipient must be configured for email notification '$id'" }
    val finalSubject = subject.ifBlank { error("Email subject must be set for notification '$id'") }
    val finalBody = body.ifBlank { error("Email body must be set for notification '$id'") }
    val config = EmailNotificationConfig(
        from = from,
        to = toRecipients,
        subject = finalSubject,
        body = finalBody,
        contentType = contentType,
        cc = cc.normalizeAddresses(),
        bcc = bcc.normalizeAddresses()
    )
    return notification(
        id = id,
        defaultVariables = defaultVariables
    ) {
        val rendered = config.render(variables)
        sender.send(rendered)
        emailLogger.debug { "Email notification '$id' sent for rule ${context.ruleId}" }
    }
}

fun consoleNotification(
    id: String,
    level: ConsoleLevel = ConsoleLevel.INFO,
    message: String = "[{{status}}] {{ruleMessage}} :: {{problemDetails}} (sample={{sampleCount}}, total={{totalMatchCount}}) @ {{timestamp}}",
    defaultVariables: Map<String, String> = emptyMap()
): NotificationDefinition {
    return notification(
        id = id,
        defaultVariables = defaultVariables
    ) {
        val rendered = render(message)
        when (level) {
            ConsoleLevel.TRACE -> emailLogger.trace { rendered }
            ConsoleLevel.DEBUG -> emailLogger.debug { rendered }
            ConsoleLevel.INFO -> emailLogger.info { rendered }
            ConsoleLevel.WARN -> emailLogger.warn { rendered }
            ConsoleLevel.ERROR -> emailLogger.error { rendered }
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
        retrySuspend(
            description = "sendgrid",
            maxAttempts = config.maxRetries,
            initialDelay = config.retryDelay,
            shouldRetry = { throwable -> throwable is RetryableSendException }
        ) {
            val payload = buildPayload(message)
            val response = httpClient.post(config.endpoint) {
                header("Authorization", "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
            handleResponse(response)
        }
    }

    private fun buildPayload(alertNotification: EmailMessage): String = buildJsonObject {
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
    }.toString()

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
