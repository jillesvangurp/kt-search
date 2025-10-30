package com.jillesvangurp.ktsearch.alert.notifications

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.forms.FormDataContent
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
    val provider: String
    suspend fun send(message: SmsMessage)
}

class SmsNotificationHandler(
    senders: List<SmsSender>
) : NotificationHandler {
    override val channel: NotificationChannel = NotificationChannel.SMS

    private val senderByProvider: Map<String, SmsSender> = senders.associateBy { it.provider }

    override suspend fun send(
        definition: NotificationDefinition,
        variables: Map<String, String>,
        context: NotificationContext
    ) {
        val config = definition.config as? SmsNotificationConfig
            ?: error("Notification '${definition.id}' is not configured as SMS")
        val sender = senderByProvider[config.provider]
            ?: error("No SMS sender registered for provider '${config.provider}'")
        val message = SmsMessage(
            senderId = config.senderId,
            recipients = config.to,
            body = renderTemplate(config.message, variables)
        )
        try {
            sender.send(message)
        } catch (t: Throwable) {
            smsLogger.error(t) {
                "SMS notification '${definition.id}' failed for provider '${config.provider}'"
            }
            throw t
        }
        smsLogger.debug { "SMS notification '${definition.id}' sent for rule ${context.ruleId}" }
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
    override val provider: String = "twilio"

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
