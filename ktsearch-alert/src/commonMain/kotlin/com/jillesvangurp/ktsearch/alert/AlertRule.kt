package com.jillesvangurp.ktsearch.alert

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@DslMarker
annotation class AlertRuleDslMarker

@Serializable
data class EmailTemplate(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String = "text/plain",
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val placeholders: Map<String, String> = emptyMap()
)

data class RenderedEmail(
    val from: String,
    val to: List<String>,
    val subject: String,
    val body: String,
    val contentType: String,
    val cc: List<String>,
    val bcc: List<String>
)

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

@AlertRuleDslMarker
class EmailTemplateBuilder {
    private var from: String? = null
    private val to = linkedSetOf<String>()
    private val cc = linkedSetOf<String>()
    private val bcc = linkedSetOf<String>()
    var subject: String? = null
    var body: String? = null
    var contentType: String = "text/plain"
    private val placeholders = mutableMapOf<String, String>()

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

    fun placeholder(key: String, value: String) {
        placeholders[key] = value
    }

    fun build(): EmailTemplate {
        val fromAddress = from ?: error("From address must be set for email template")
        val recipients = to.toList()
        require(recipients.isNotEmpty()) { "At least one recipient must be configured" }
        val finalSubject = subject ?: error("Email subject must be set")
        val finalBody = body ?: error("Email body must be set")
        return EmailTemplate(
            from = fromAddress,
            to = recipients,
            subject = finalSubject,
            body = finalBody,
            contentType = contentType,
            cc = cc.toList(),
            bcc = bcc.toList(),
            placeholders = placeholders.toMap()
        )
    }
}

fun EmailTemplate.render(rule: AlertRule, matches: List<JsonObject>, now: Instant = currentInstant()): RenderedEmail {
    val context = buildMap {
        put("ruleName", rule.name)
        put("ruleId", rule.id)
        put("matchCount", matches.size.toString())
        put("timestamp", now.toString())
        putAll(placeholders)
    }
    return RenderedEmail(
        from = from,
        to = to,
        subject = applyTemplate(subject, context),
        body = applyTemplate(body, context),
        contentType = contentType,
        cc = cc,
        bcc = bcc
    )
}

private fun applyTemplate(template: String, context: Map<String, String>): String =
    context.entries.fold(template) { acc, (key, value) ->
        acc.replace("{{${key}}}", value)
    }
