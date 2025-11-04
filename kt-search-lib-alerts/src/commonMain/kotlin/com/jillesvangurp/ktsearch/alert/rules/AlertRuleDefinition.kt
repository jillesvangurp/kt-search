package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlin.time.Duration

data class AlertRuleDefinition(
    val id: String?,
    val name: String,
    val enabled: Boolean,
    val cronExpression: String,
    val target: String,
    val queryJson: String,
    val message: String?,
    val failureMessage: String?,
    val notifications: List<RuleNotificationInvocation>,
    val failureNotifications: List<RuleNotificationInvocation> = emptyList(),
    val repeatNotificationIntervalMillis: Long?,
    val startImmediately: Boolean
) {
    companion object {
        fun newRule(
            id: String? = null,
            name: String,
            cronExpression: String,
            target: String,
            message: String? = null,
            failureMessage: String? = null,
            notifications: List<RuleNotificationInvocation>,
            failureNotifications: List<RuleNotificationInvocation> = emptyList(),
            enabled: Boolean = true,
            startImmediately: Boolean = true,
            repeatNotificationsEvery: Duration? = null,
            query: SearchDSL.() -> Unit
        ): AlertRuleDefinition = fromQueryJson(
            id = id,
            name = name,
            cronExpression = cronExpression,
            target = target,
            message = message,
            failureMessage = failureMessage,
            notifications = notifications,
            failureNotifications = failureNotifications,
            enabled = enabled,
            startImmediately = startImmediately,
            repeatNotificationsEvery = repeatNotificationsEvery,
            queryJson = SearchDSL().apply(query).json()
        )

        fun fromQueryJson(
            id: String? = null,
            name: String,
            cronExpression: String,
            target: String,
            message: String? = null,
            failureMessage: String? = null,
            notifications: List<RuleNotificationInvocation>,
            failureNotifications: List<RuleNotificationInvocation> = emptyList(),
            enabled: Boolean = true,
            startImmediately: Boolean = true,
            repeatNotificationsEvery: Duration? = null,
            queryJson: String
        ): AlertRuleDefinition {
            require(name.isNotBlank()) { "Rule name must be specified" }
            require(cronExpression.isNotBlank()) { "Cron expression must be specified" }
            require(target.isNotBlank()) { "Target index or alias must be specified" }
            require(queryJson.isNotBlank()) { "Query must be provided for rule '$name'" }
            val repeatMillis = repeatNotificationsEvery?.let {
                require(it >= Duration.ZERO) { "Repeat notification interval must not be negative" }
                it.inWholeMilliseconds
            }
            return AlertRuleDefinition(
                id = id,
                name = name,
                enabled = enabled,
                cronExpression = cronExpression,
                target = target,
                queryJson = queryJson,
                message = normalizeMessage(message),
                failureMessage = normalizeMessage(failureMessage),
                notifications = notifications.normalizeInvocations(),
                failureNotifications = failureNotifications.normalizeInvocations(),
                repeatNotificationIntervalMillis = repeatMillis,
                startImmediately = startImmediately
            )
        }

        private fun List<RuleNotificationInvocation>.normalizeInvocations(): List<RuleNotificationInvocation> =
            map { it.copy(variables = it.variables.toMap()) }

        private fun normalizeMessage(message: String?): String? =
            message?.trim()?.takeIf { it.isNotEmpty() }
    }
}
