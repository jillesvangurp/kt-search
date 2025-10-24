package com.jillesvangurp.ktsearch.alert.rules

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class AlertRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val cronExpression: String,
    val target: String,
    val queryJson: String,
    val notifications: List<RuleNotificationInvocation>,
    val failureNotifications: List<RuleNotificationInvocation> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val lastRun: Instant? = null,
    val nextRun: Instant? = null,
    val failureCount: Int = 0,
    val lastFailureMessage: String? = null
) {
    fun executionHash(): Int {
        var result = cronExpression.hashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + queryJson.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + notifications.hashCode()
        result = 31 * result + failureNotifications.hashCode()
        return result
    }
}

@Serializable
data class RuleNotificationInvocation(
    val notificationId: String,
    val variables: Map<String, String> = emptyMap()
) {
    companion object {
        fun create(
            notificationId: String,
            variables: Map<String, String> = emptyMap()
        ): RuleNotificationInvocation {
            require(notificationId.isNotBlank()) { "Notification id must not be blank" }
            return RuleNotificationInvocation(notificationId, variables.toMap())
        }

        fun withVariables(
            notificationId: String,
            vararg variables: Pair<String, String>
        ): RuleNotificationInvocation = create(notificationId, mapOf(*variables))

        fun many(
            vararg notificationIds: String,
            variables: Map<String, String> = emptyMap()
        ): List<RuleNotificationInvocation> {
            require(notificationIds.isNotEmpty()) { "At least one notification id must be provided" }
            return notificationIds.map { create(it, variables) }
        }
    }
}
