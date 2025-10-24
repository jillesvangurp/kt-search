package com.jillesvangurp.ktsearch.alert.notifications

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class NotificationDispatcherConfig(
    val emailRetryAttempts: Int = 3,
    val emailRetryDelay: Duration = 2.seconds,
    val slackRetryAttempts: Int = 3,
    val slackRetryDelay: Duration = 2.seconds,
    val smsRetryAttempts: Int = 3,
    val smsRetryDelay: Duration = 2.seconds,
    val includeConsole: Boolean = true,
    val additionalHandlers: List<NotificationHandler> = emptyList()
)

fun createNotificationDispatcher(
    emailSender: EmailSender? = null,
    slackSender: SlackSender? = null,
    smsSenders: List<SmsSender> = emptyList(),
    config: NotificationDispatcherConfig = NotificationDispatcherConfig()
): NotificationDispatcher {
    val handlers = buildList {
        if (emailSender != null) {
            add(EmailNotificationHandler(emailSender))
        }
        if (slackSender != null) {
            add(
                SlackNotificationHandler(
                    sender = slackSender,
                    maxRetries = config.slackRetryAttempts,
                    retryDelay = config.slackRetryDelay
                )
            )
        }
        if (smsSenders.isNotEmpty()) {
            add(
                SmsNotificationHandler(
                    senders = smsSenders,
                    maxRetries = config.smsRetryAttempts,
                    retryDelay = config.smsRetryDelay
                )
            )
        }
        if (config.includeConsole) {
            add(ConsoleNotificationHandler())
        }
        addAll(config.additionalHandlers)
    }
    require(handlers.isNotEmpty()) { "At least one notification handler must be configured" }
    return NotificationDispatcher(handlers)
}
