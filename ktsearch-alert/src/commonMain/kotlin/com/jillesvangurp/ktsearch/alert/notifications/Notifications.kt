package com.jillesvangurp.ktsearch.alert.notifications

import kotlin.collections.set

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
)

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

@DslMarker
annotation class NotificationDslMarker

@NotificationDslMarker
class NotificationsDsl internal constructor() {
    private val definitions = linkedMapOf<String, NotificationDefinition>()

    fun email(id: String, block: EmailNotificationBuilder.() -> Unit) {
        addDefinition(id, EmailNotificationBuilder().apply(block).build(id))
    }

    fun slack(id: String, block: SlackNotificationBuilder.() -> Unit) {
        addDefinition(id, SlackNotificationBuilder().apply(block).build(id))
    }

    fun sms(id: String, block: SmsNotificationBuilder.() -> Unit) {
        addDefinition(id, SmsNotificationBuilder().apply(block).build(id))
    }

    fun console(id: String, block: ConsoleNotificationBuilder.() -> Unit = {}) {
        addDefinition(id, ConsoleNotificationBuilder().apply(block).build(id))
    }

    fun definition(notificationDefinition: NotificationDefinition) {
        addDefinition(notificationDefinition.id, notificationDefinition)
    }

    internal fun build(): NotificationRegistry = NotificationRegistry(definitions.toMap())

    private fun addDefinition(id: String, definition: NotificationDefinition) {
        require(id.isNotBlank()) { "Notification id must not be blank" }
        require(definitions[id] == null) { "Notification '$id' already defined" }
        definitions[id] = definition
    }
}

@NotificationDslMarker
class EmailNotificationBuilder {
    private var from: String? = null
    private val to = linkedSetOf<String>()
    private val cc = linkedSetOf<String>()
    private val bcc = linkedSetOf<String>()
    var subject: String? = null
    var body: String? = null
    var contentType: String = "text/plain"
    private val defaultVariables = linkedMapOf<String, String>()

    fun from(address: String) {
        from = address
    }

    fun to(vararg addresses: String) {
        to += addresses
    }

    fun cc(vararg addresses: String) {
        cc += addresses
    }

    fun bcc(vararg addresses: String) {
        bcc += addresses
    }

    fun defaultVariable(key: String, value: String) {
        defaultVariables[key] = value
    }

    internal fun build(id: String): NotificationDefinition {
        val fromAddress = from ?: error("From address must be set for email notification '$id'")
        val recipients = to.toList()
        require(recipients.isNotEmpty()) { "At least one recipient must be configured for email notification '$id'" }
        val finalSubject = subject ?: error("Email subject must be set for notification '$id'")
        val finalBody = body ?: error("Email body must be set for notification '$id'")
        val config = EmailNotificationConfig(
            from = fromAddress,
            to = recipients,
            subject = finalSubject,
            body = finalBody,
            contentType = contentType,
            cc = cc.toList(),
            bcc = bcc.toList()
        )
        return NotificationDefinition(
            id = id,
            config = config,
            defaultVariables = defaultVariables.toMap()
        )
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

@NotificationDslMarker
class SlackNotificationBuilder {
    var webhookUrl: String? = null
    var channelName: String? = null
    var username: String? = null
    var message: String? = null
    private val defaultVariables = linkedMapOf<String, String>()

    fun defaultVariable(key: String, value: String) {
        defaultVariables[key] = value
    }

    internal fun build(id: String): NotificationDefinition {
        val url = webhookUrl ?: error("Slack webhookUrl must be set for notification '$id'")
        val messageTemplate = message ?: error("Slack message must be set for notification '$id'")
        val config = SlackNotificationConfig(
            webhookUrl = url,
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
}

data class SlackNotificationConfig(
    val webhookUrl: String,
    val channelName: String? = null,
    val username: String? = null,
    val message: String
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.SLACK
}

@NotificationDslMarker
class SmsNotificationBuilder {
    var provider: String? = null
    var senderId: String? = null
    private val to = linkedSetOf<String>()
    var message: String? = null
    private val defaultVariables = linkedMapOf<String, String>()

    fun to(vararg numbers: String) {
        to += numbers
    }

    fun defaultVariable(key: String, value: String) {
        defaultVariables[key] = value
    }

    internal fun build(id: String): NotificationDefinition {
        val providerName = provider ?: error("SMS provider must be set for notification '$id'")
        val recipients = to.toList()
        require(recipients.isNotEmpty()) { "At least one recipient must be set for SMS notification '$id'" }
        val messageTemplate = message ?: error("SMS message must be set for notification '$id'")
        val config = SmsNotificationConfig(
            provider = providerName,
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
}

data class SmsNotificationConfig(
    val provider: String,
    val senderId: String?,
    val to: List<String>,
    val message: String
) : NotificationConfig {
    override val channel: NotificationChannel = NotificationChannel.SMS
}

@NotificationDslMarker
class ConsoleNotificationBuilder {
    var level: ConsoleLevel = ConsoleLevel.INFO
    var message: String = "{{ruleName}} triggered with {{matchCount}} matches"
    private val defaultVariables = linkedMapOf<String, String>()

    fun defaultVariable(key: String, value: String) {
        defaultVariables[key] = value
    }

    internal fun build(id: String): NotificationDefinition {
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
