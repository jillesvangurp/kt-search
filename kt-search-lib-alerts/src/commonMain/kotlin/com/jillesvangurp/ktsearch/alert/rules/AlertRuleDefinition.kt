package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.alert.core.RuleConfigurationScope
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition.ClusterStatusRule
import com.jillesvangurp.ktsearch.alert.rules.RuleCheck.Search
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlin.time.Duration

interface AlertRuleDefinition {
    val id: String
    val name: String?
    val enabled: Boolean
    val cronExpression: String
    val target: String
    val message: String?
    val failureMessage: String?
    val notifications: List<RuleNotificationInvocation>
    val failureNotifications: List<RuleNotificationInvocation>
    val repeatNotificationIntervalMillis: Long?
    val startImmediately: Boolean
    val check: RuleCheck

    data class Search(
        override val id: String,
        override val name: String?,
        override val enabled: Boolean,
        override val cronExpression: String,
        override val target: String,
        override val message: String?,
        override val failureMessage: String?,
        override val notifications: List<RuleNotificationInvocation>,
        override val failureNotifications: List<RuleNotificationInvocation>,
        override val repeatNotificationIntervalMillis: Long?,
        override val startImmediately: Boolean,
        val firingCondition: RuleFiringCondition,
        val searchCheck: RuleCheck.Search
    ) : AlertRuleDefinition {
        override val check: RuleCheck get() = searchCheck
    }

    data class ClusterStatusRule(
        override val id: String,
        override val name: String?,
        override val enabled: Boolean,
        override val cronExpression: String,
        override val target: String,
        override val message: String?,
        override val failureMessage: String?,
        override val notifications: List<RuleNotificationInvocation>,
        override val failureNotifications: List<RuleNotificationInvocation>,
        override val repeatNotificationIntervalMillis: Long?,
        override val startImmediately: Boolean,
        val clusterCheck: RuleCheck.ClusterStatusCheck
    ) : AlertRuleDefinition {
        override val check: RuleCheck get() = clusterCheck
    }
}

fun RuleConfigurationScope.newSearchRule(
    id: String,
    cronExpression: String,
    target: String,
    name: String = id,
    message: String? = null,
    failureMessage: String? = null,
    notifications: List<RuleNotificationInvocation> = emptyList(),
    failureNotifications: List<RuleNotificationInvocation> = emptyList(),
    enabled: Boolean = true,
    startImmediately: Boolean = true,
    repeatNotificationsEvery: Duration? = null,
    firingCondition: RuleFiringCondition = RuleFiringCondition.AtMost(0),
    query: SearchDSL.() -> Unit
): AlertRuleDefinition.Search {
    val queryJson = SearchDSL().apply(query).json()
    require(id.isNotBlank()) { "Rule id must be specified" }
    require(cronExpression.isNotBlank()) { "Cron expression must be specified" }
    require(target.isNotBlank()) { "Target index or alias must be specified" }
    require(queryJson.isNotBlank()) { "Query must be provided for rule '$name'" }
    val repeatMillis = repeatNotificationsEvery?.let {
        require(it >= Duration.ZERO) { "Repeat notification interval must not be negative" }
        it.inWholeMilliseconds
    }
    return AlertRuleDefinition.Search(
        id = id,
        name = name,
        enabled = enabled,
        cronExpression = cronExpression,
        target = target,
        message = normalizeMessage(message = message),
        failureMessage = normalizeMessage(message = failureMessage),
        notifications = notifications.normalizeInvocations(),
        failureNotifications = failureNotifications.normalizeInvocations(),
        repeatNotificationIntervalMillis = repeatMillis,
        startImmediately = startImmediately,
        firingCondition = firingCondition,
        searchCheck = Search(target = target, queryJson = queryJson)
    )
}

fun RuleConfigurationScope.clusterStatusRule(
    id: String,
    name: String = id,
    cronExpression: String,
    expectedStatus: ClusterStatus = ClusterStatus.Green,
    description: String = "cluster",
    message: String? = null,
    failureMessage: String? = null,
    notifications: List<RuleNotificationInvocation> = emptyList(),
    failureNotifications: List<RuleNotificationInvocation> = emptyList(),
    enabled: Boolean = true,
    startImmediately: Boolean = true,
    repeatNotificationsEvery: Duration? = null,
): ClusterStatusRule {
    require(name.isNotBlank()) { "Rule name must be specified" }
    require(cronExpression.isNotBlank()) { "Cron expression must be specified" }
    require(description.isNotBlank()) { "Description must be specified for rule '$name'" }
    val repeatMillis = repeatNotificationsEvery?.let {
        require(it >= Duration.ZERO) { "Repeat notification interval must not be negative" }
        it.inWholeMilliseconds
    }
    return ClusterStatusRule(
        id = id,
        name = name,
        enabled = enabled,
        cronExpression = cronExpression,
        target = description,
        message = normalizeMessage(message),
        failureMessage = normalizeMessage(failureMessage),
        notifications = notifications.normalizeInvocations(),
        failureNotifications = failureNotifications.normalizeInvocations(),
        repeatNotificationIntervalMillis = repeatMillis,
        startImmediately = startImmediately,
        clusterCheck = RuleCheck.ClusterStatusCheck(
            expectedStatus = expectedStatus,
            description = description
        )
    )
}

private fun normalizeMessage(message: String?): String? =
    message?.trim()?.takeIf { it.isNotEmpty() }

private fun List<RuleNotificationInvocation>.normalizeInvocations(): List<RuleNotificationInvocation> =
    map { it.copy(variables = it.variables.toMap()) }

