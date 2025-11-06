package com.jillesvangurp.ktsearch.alert.notifications

import kotlinx.serialization.json.JsonObject
import kotlin.collections.LinkedHashSet
import kotlin.time.Instant

/**
 * Represents a fully configured notification. The [sender] lambda encapsulates
 * everything required to deliver the notification so the alert service does not
 * need a separate dispatcher or handler registry.
 */
class NotificationDefinition(
    val id: String,
    val defaultVariables: Map<String, String>,
    private val sender: suspend NotificationSendScope.() -> Unit
) {
    suspend fun dispatch(variables: Map<String, String>, context: NotificationContext) {
        sender(NotificationSendScope(context, variables))
    }
}

/**
 * Scope exposed to notification delivery blocks. Provides the runtime
 * notification [context] and the rendered [variables].
 */
class NotificationSendScope internal constructor(
    val context: NotificationContext,
    val variables: Map<String, String>
) {
    fun render(template: String): String = renderTemplate(template, variables)
}

/**
 * Creates a [NotificationDefinition] with a suspending delivery block. This
 * function underpins the notification DSL and keeps each notification
 * self-contained.
 */
fun notification(
    id: String,
    defaultVariables: Map<String, String> = emptyMap(),
    sender: suspend NotificationSendScope.() -> Unit
): NotificationDefinition {
    require(id.isNotBlank()) { "Notification id must not be blank" }
    return NotificationDefinition(
        id = id,
        defaultVariables = defaultVariables.toMap(),
        sender = sender
    )
}

class NotificationRegistry internal constructor(
    private val definitions: Map<String, NotificationDefinition>
) {
    fun get(id: String): NotificationDefinition? = definitions[id]

    fun require(id: String): NotificationDefinition =
        get(id) ?: error("Notification '$id' is not defined")

    fun all(): Collection<NotificationDefinition> = definitions.values

    companion object {
        fun empty(): NotificationRegistry = NotificationRegistry(emptyMap())
    }
}

data class NotificationContext(
    val ruleId: String,
    val ruleName: String,
    val triggeredAt: Instant,
    val matchCount: Int,
    val matches: List<JsonObject>,
    val resultDescription: String? = null,
    val totalMatchCount: Long? = null,
    val problemDetails: String? = null
)

enum class ConsoleLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR
}

internal fun Collection<String>.normalizeAddresses(): List<String> {
    val unique = LinkedHashSet<String>()
    for (value in this) {
        if (value.isNotEmpty()) {
            unique += value
        }
    }
    return unique.toList()
}
