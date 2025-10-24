package com.jillesvangurp.ktsearch.alert.rules

data class AlertRuleDefinition(
    val id: String?,
    val name: String,
    val enabled: Boolean,
    val cronExpression: String,
    val target: String,
    val queryJson: String,
    val notifications: List<RuleNotificationInvocation>,
    val failureNotifications: List<RuleNotificationInvocation> = emptyList(),
    val startImmediately: Boolean
)
