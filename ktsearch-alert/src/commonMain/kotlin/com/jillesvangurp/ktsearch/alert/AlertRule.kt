package com.jillesvangurp.ktsearch.alert

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
    val emailTemplate: EmailTemplate,
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
        result = 31 * result + emailTemplate.hashCode()
        result = 31 * result + enabled.hashCode()
        return result
    }
}

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
    private var emailTemplateBuilder: EmailTemplateBuilder? = null

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

    fun email(block: EmailTemplateBuilder.() -> Unit) {
        val builder = EmailTemplateBuilder()
        builder.apply(block)
        emailTemplateBuilder = builder
    }

    fun build(): AlertRuleDefinition {
        val finalName = name ?: error("Rule name must be specified")
        val cronExpression = cron ?: error("Cron expression must be specified")
        val targetIndex = target ?: error("Target index or alias must be specified")
        val queryPayload = queryJson ?: error("Query must be provided for rule '$finalName'")
        val emailTemplate = emailTemplateBuilder?.build()
            ?: error("Email template must be configured for rule '$finalName'")
        return AlertRuleDefinition(
            id = presetId,
            name = finalName,
            enabled = enabled,
            cronExpression = cronExpression,
            target = targetIndex,
            queryJson = queryPayload,
            emailTemplate = emailTemplate,
            startImmediately = startImmediately
        )
    }
}
