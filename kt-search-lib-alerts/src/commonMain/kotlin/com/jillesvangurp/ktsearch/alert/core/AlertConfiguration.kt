package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import kotlin.collections.LinkedHashSet

data class AlertConfiguration(
    val notifications: NotificationRegistry,
    val rules: List<AlertRuleDefinition>,
    val defaultNotificationIds: List<String>
)

@DslMarker
annotation class AlertConfigurationDslMarker

@AlertConfigurationDslMarker
class AlertConfigurationBuilder internal constructor() {
    private val notificationDefinitions = linkedMapOf<String, NotificationDefinition>()
    private val ruleDefinitions = mutableListOf<AlertRuleDefinition>()
    private val defaultNotificationIds = mutableListOf<String>()

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

    fun defaultNotifications(vararg notificationIds: String) {
        defaultNotifications(notificationIds.asIterable())
    }

    fun defaultNotifications(notificationIds: Iterable<String>) {
        notificationIds.forEach { id ->
            require(id.isNotBlank()) { "Default notification id must not be blank" }
            defaultNotificationIds += id
        }
    }

    internal fun build(): AlertConfiguration {
        val definitions = notificationDefinitions.toMap()
        val defaults = LinkedHashSet<String>()
        defaultNotificationIds.forEach { id ->
            require(definitions.containsKey(id)) { "Default notification '$id' is not defined" }
            defaults += id
        }
        return AlertConfiguration(
            notifications = NotificationRegistry(definitions),
            rules = ruleDefinitions.toList(),
            defaultNotificationIds = defaults.toList()
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
