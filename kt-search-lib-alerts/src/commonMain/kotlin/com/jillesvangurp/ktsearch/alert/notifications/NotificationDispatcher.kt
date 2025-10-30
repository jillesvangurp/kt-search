package com.jillesvangurp.ktsearch.alert.notifications

import com.jillesvangurp.ktsearch.alert.core.retrySuspend
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

data class NotificationContext(
    val ruleId: String,
    val ruleName: String,
    val triggeredAt: kotlin.time.Instant,
    val matchCount: Int,
    val matches: List<JsonObject>
)

interface NotificationHandler {
    val channel: NotificationChannel
    suspend fun send(
        definition: NotificationDefinition,
        variables: Map<String, String>,
        context: NotificationContext
    )
}

class NotificationDispatcher(
    handlers: List<NotificationHandler>,
    private val retryAttempts: Int = 3,
    private val retryDelay: Duration = 2.seconds
) {
    private val handlerByChannel = handlers.associateBy { it.channel }

    suspend fun dispatch(definition: NotificationDefinition, variables: Map<String, String>, context: NotificationContext) {
        val handler = handlerByChannel[definition.config.channel]
            ?: error("No handler registered for notification channel ${definition.config.channel}")

        retrySuspend("notification-${definition.config.channel}", retryAttempts, retryDelay) {
            handler.send(definition, variables, context)
        }
    }

    fun hasHandler(channel: NotificationChannel): Boolean = handlerByChannel.containsKey(channel)
}
