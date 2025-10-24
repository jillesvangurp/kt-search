package com.jillesvangurp.ktsearch.alert.notifications

import kotlin.collections.LinkedHashSet

enum class NotificationChannel {
    EMAIL,
    SLACK,
    SMS,
    CONSOLE
}

sealed interface NotificationConfig {
    val channel: NotificationChannel
}

data class NotificationDefinition(
    val id: String,
    val config: NotificationConfig,
    val defaultVariables: Map<String, String> = emptyMap()
) {
    companion object {
        fun email(
            id: String,
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
            return NotificationDefinition(
                id = id,
                config = config,
                defaultVariables = defaultVariables.toMap()
            )
        }

        fun slack(
            id: String,
            webhookUrl: String,
            message: String,
            channelName: String? = null,
            username: String? = null,
            defaultVariables: Map<String, String> = emptyMap()
        ): NotificationDefinition {
            require(webhookUrl.isNotBlank()) { "Slack webhookUrl must be set for notification '$id'" }
            val messageTemplate = message.ifBlank { error("Slack message must be set for notification '$id'") }
            val config = SlackNotificationConfig(
                webhookUrl = webhookUrl,
                channelName = channelName,
                username = username,
                message = messageTemplate
            )
            return NotificationDefinition(
                id = id,
                config = config,
                defaultVariables = defaultVariables.toMap()
            )
        }

        fun sms(
            id: String,
            provider: String,
            to: List<String>,
            message: String,
            senderId: String? = null,
            defaultVariables: Map<String, String> = emptyMap()
        ): NotificationDefinition {
            require(provider.isNotBlank()) { "SMS provider must be set for notification '$id'" }
            val recipients = to.normalizeAddresses()
            require(recipients.isNotEmpty()) { "At least one recipient must be set for SMS notification '$id'" }
            val messageTemplate = message.ifBlank { error("SMS message must be set for notification '$id'") }
            val config = SmsNotificationConfig(
                provider = provider,
                senderId = senderId,
                to = recipients,
                message = messageTemplate
            )
            return NotificationDefinition(
                id = id,
                config = config,
                defaultVariables = defaultVariables.toMap()
            )
        }

        fun console(
            id: String,
            level: ConsoleLevel = ConsoleLevel.INFO,
            message: String = "{{ruleName}} triggered with {{matchCount}} matches",
            defaultVariables: Map<String, String> = emptyMap()
        ): NotificationDefinition {
            val config = ConsoleNotificationConfig(
                level = level,
                message = message
            )
            return NotificationDefinition(
                id = id,
                config = config,
                defaultVariables = defaultVariables.toMap()
            )
        }
    }
}

class NotificationRegistry internal constructor(
    private val definitions: Map<String, NotificationDefinition>
) {
    fun get(id: String): NotificationDefinition? = definitions[id]

    fun require(id: String): NotificationDefinition =
        get(id) ?: error("Notification '$id' is not defined")

    fun all(): Collection<NotificationDefinition> = definitions.values

    companion object {
        fun empty(): NotificationRegistry = NotificationRegistry(emptyMap())
    }
}

data class EmailNotificationConfig(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String = "text/plain",
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList()
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.EMAIL
}

data class SlackNotificationConfig(
    val webhookUrl: String,
    val channelName: String? = null,
    val username: String? = null,
    val message: String
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.SLACK
}

data class SmsNotificationConfig(
    val provider: String,
    val senderId: String?,
    val to: List<String>,
    val message: String
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.SMS
}

data class ConsoleNotificationConfig(
    val level: ConsoleLevel,
    val message: String
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.CONSOLE
}

enum class ConsoleLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

private fun Collection<String>.normalizeAddresses(): List<String> {
    val unique = LinkedHashSet<String>()
    for (value in this) {
        if (value.isNotEmpty()) {
            unique += value
        }
    }
    return unique.toList()
}
