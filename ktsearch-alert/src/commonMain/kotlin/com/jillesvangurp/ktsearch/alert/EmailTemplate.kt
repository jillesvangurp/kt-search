package com.jillesvangurp.ktsearch.alert

import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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

fun EmailTemplate.render(rule: AlertRule, matches: List<JsonObject>, now: Instant = currentInstant()): AlertNotification {
    val context = buildMap {
        put("ruleName", rule.name)
        put("ruleId", rule.id)
        put("matchCount", matches.size.toString())
        put("timestamp", now.toString())
        putAll(placeholders)
    }
    return AlertNotification(
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
