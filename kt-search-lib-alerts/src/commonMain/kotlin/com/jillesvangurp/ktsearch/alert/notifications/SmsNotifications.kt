package com.jillesvangurp.ktsearch.alert.notifications

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val smsLogger = KotlinLogging.logger {}

data class SmsMessage(
    val senderId: String?,
    val recipients: List<String>,
    val body: String
)

interface SmsSender {
    suspend fun send(message: SmsMessage)
}

fun smsNotification(
    id: String,
    sender: SmsSender,
    to: List<String>,
    message: String,
    senderId: String? = null,
    defaultVariables: Map<String, String> = emptyMap()
): NotificationDefinition {
    val recipients = to.normalizeAddresses()
    require(recipients.isNotEmpty()) { "At least one recipient must be set for SMS notification '$id'" }
    val messageTemplate = message.ifBlank { error("SMS message must be set for notification '$id'") }
    return notification(
        id = id,
        defaultVariables = defaultVariables
    ) {
        val smsMessage = SmsMessage(
            senderId = senderId,
            recipients = recipients,
            body = render(messageTemplate)
        )
        runCatching { sender.send(smsMessage) }
            .onFailure { failure ->
                smsLogger.error(failure) { "SMS notification '$id' failed for rule ${context.ruleId}" }
                throw failure
            }
            .onSuccess {
                smsLogger.debug { "SMS notification '$id' sent for rule ${context.ruleId}" }
            }
    }
}

data class TwilioConfig(
    val accountSid: String,
    val authToken: String,
    val defaultSenderId: String? = null,
    val apiBaseUrl: String = "https://api.twilio.com/2010-04-01"
)

class TwilioSmsSender(
    private val httpClient: HttpClient,
    private val config: TwilioConfig
) : SmsSender {
    override suspend fun send(message: SmsMessage) {
        val from = message.senderId ?: config.defaultSenderId
            ?: error("Twilio SMS requires a senderId in the notification or defaultSenderId in TwilioConfig")
        for (recipient in message.recipients) {
            try {
                val response = httpClient.post("${config.apiBaseUrl}/Accounts/${config.accountSid}/Messages.json") {
                    header(HttpHeaders.Authorization, "Basic ${basicAuthValue(config.accountSid, config.authToken)}")
                    contentType(ContentType.Application.FormUrlEncoded)
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("To", recipient)
                                append("From", from)
                                append("Body", message.body)
                            }
                        )
                    )
                }
                if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrElse { "" }
                    val status = response.status.value
                    val exception = TwilioException(status, body)
                    val failure = if (status == 429 || status >= 500) {
                        RetryableTwilioException(exception)
                    } else {
                        exception
                    }
                    throw failure
                }
            } catch (t: Throwable) {
                smsLogger.error(t) {
                    "Twilio SMS request failed for recipient $recipient"
                }
                throw t
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun basicAuthValue(accountSid: String, authToken: String): String =
        Base64.Default.encode("${accountSid}:${authToken}".encodeToByteArray())
}

open class TwilioException(val statusCode: Int, body: String) :
    Exception("Twilio error $statusCode: $body")

class RetryableTwilioException(cause: TwilioException) : Exception(cause)
