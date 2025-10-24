package com.jillesvangurp.ktsearch.alert

data class AlertRuleDefinition(
    val id: String?,
    val name: String,
    val enabled: Boolean,
    val cronExpression: String,
    val target: String,
    val queryJson: String,
    val emailTemplate: EmailTemplate,
    val startImmediately: Boolean
)