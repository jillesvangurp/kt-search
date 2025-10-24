package com.jillesvangurp.ktsearch.alert.rules

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@DslMarker
annotation class AlertRuleDslMarker

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
)

@AlertRuleDslMarker
class AlertRulesDsl internal constructor() {
    private val definitions = mutableListOf<AlertRuleDefinition>()

    fun rule(id: String? = null, block: AlertRuleBuilder.() -> Unit) {
        val builder = AlertRuleBuilder(id)
        builder.apply(block)
        definitions += builder.build()
    }

    internal fun build(): List<AlertRuleDefinition> = definitions.toList()
}

fun alertRules(block: AlertRulesDsl.() -> Unit): List<AlertRuleDefinition> =
    AlertRulesDsl().apply(block).build()

@AlertRuleDslMarker
class AlertRuleBuilder internal constructor(private val presetId: String?) {
    var name: String? = null
    var enabled: Boolean = true
    var cron: String? = null
    private var target: String? = null
    private var queryJson: String? = null
    private var startImmediately: Boolean = true
    private val notificationInvocations = mutableListOf<RuleNotificationInvocation>()
    private val failureNotificationInvocations = mutableListOf<RuleNotificationInvocation>()

    fun target(indexOrAlias: String) {
        target = indexOrAlias
    }

    fun cron(expression: String) {
        cron = expression
    }

    fun schedule(expression: String) = cron(expression)

    fun startImmediately(value: Boolean) {
        startImmediately = value
    }

    fun query(block: SearchDSL.() -> Unit) {
        val dsl = SearchDSL().apply(block)
        queryJson = dsl.json()
    }

    fun notifications(vararg ids: String, block: NotificationVariablesBuilder.() -> Unit = {}) {
        val variables = NotificationVariablesBuilder().apply(block).build()
        ids.forEach { id ->
            notificationInvocations += RuleNotificationInvocation(id, variables)
        }
    }

    fun notification(id: String, block: NotificationInvocationBuilder.() -> Unit = {}) {
        val builder = NotificationInvocationBuilder(id)
        builder.apply(block)
        notificationInvocations += builder.build()
    }

    fun failureNotifications(vararg ids: String, block: NotificationVariablesBuilder.() -> Unit = {}) {
        val variables = NotificationVariablesBuilder().apply(block).build()
        ids.forEach { id ->
            failureNotificationInvocations += RuleNotificationInvocation(id, variables)
        }
    }

    fun failureNotification(id: String, block: NotificationInvocationBuilder.() -> Unit = {}) {
        val builder = NotificationInvocationBuilder(id)
        builder.apply(block)
        failureNotificationInvocations += builder.build()
    }

    fun build(): AlertRuleDefinition {
        val finalName = name ?: error("Rule name must be specified")
        val cronExpression = cron ?: error("Cron expression must be specified")
        val targetIndex = target ?: error("Target index or alias must be specified")
        val queryPayload = queryJson ?: error("Query must be provided for rule '$finalName'")
        require(notificationInvocations.isNotEmpty()) { "At least one notification must be configured for rule '$finalName'" }
        return AlertRuleDefinition(
            id = presetId,
            name = finalName,
            enabled = enabled,
            cronExpression = cronExpression,
            target = targetIndex,
            queryJson = queryPayload,
            notifications = notificationInvocations.toList(),
            failureNotifications = failureNotificationInvocations.toList(),
            startImmediately = startImmediately
        )
    }
}

@AlertRuleDslMarker
class NotificationVariablesBuilder internal constructor() {
    private val variables = linkedMapOf<String, String>()

    fun variable(key: String, value: String) {
        variables[key] = value
    }

    internal fun build(): Map<String, String> = variables.toMap()
}

@AlertRuleDslMarker
class NotificationInvocationBuilder internal constructor(private val id: String) {
    private val variables = linkedMapOf<String, String>()

    fun variable(key: String, value: String) {
        variables[key] = value
    }

    internal fun build(): RuleNotificationInvocation =
        RuleNotificationInvocation(notificationId = id, variables = variables.toMap())
}
