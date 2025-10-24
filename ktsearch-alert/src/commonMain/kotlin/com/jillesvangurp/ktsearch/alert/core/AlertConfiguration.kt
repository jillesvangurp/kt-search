package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition

data class AlertConfiguration(
    val notifications: NotificationRegistry,
    val rules: List<AlertRuleDefinition>
)

@DslMarker
annotation class AlertConfigurationDslMarker

@AlertConfigurationDslMarker
class AlertConfigurationBuilder internal constructor() {
    private val notificationDefinitions = linkedMapOf<String, NotificationDefinition>()
    private val ruleDefinitions = mutableListOf<AlertRuleDefinition>()

    fun notification(definition: NotificationDefinition) {
        addNotification(definition)
    }

    fun notifications(vararg definitions: NotificationDefinition) {
        definitions.forEach(::addNotification)
    }

    fun notifications(definitions: Iterable<NotificationDefinition>) {
        definitions.forEach(::addNotification)
    }

    fun rule(definition: AlertRuleDefinition) {
        ruleDefinitions += definition
    }

    fun rules(vararg definitions: AlertRuleDefinition) {
        ruleDefinitions += definitions
    }

    fun rules(definitions: Iterable<AlertRuleDefinition>) {
        ruleDefinitions += definitions
    }

    internal fun build(): AlertConfiguration {
        return AlertConfiguration(
            notifications = NotificationRegistry(notificationDefinitions.toMap()),
            rules = ruleDefinitions.toList()
        )
    }

    private fun addNotification(definition: NotificationDefinition) {
        require(definition.id.isNotBlank()) { "Notification id must not be blank" }
        require(notificationDefinitions[definition.id] == null) { "Notification '${definition.id}' already defined" }
        notificationDefinitions[definition.id] = definition
    }
}

fun alertConfiguration(block: AlertConfigurationBuilder.() -> Unit): AlertConfiguration =
    AlertConfigurationBuilder().apply(block).build()
