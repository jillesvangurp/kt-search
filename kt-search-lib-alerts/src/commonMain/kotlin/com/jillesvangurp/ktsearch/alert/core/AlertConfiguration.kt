package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import kotlin.collections.LinkedHashSet
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

data class AlertConfiguration(
    val notifications: NotificationRegistry,
    val rules: List<AlertRuleDefinition>,
    val defaultNotificationIds: List<String>,
    val notificationDefaults: NotificationDefaults = NotificationDefaults.DEFAULT
)

@DslMarker
annotation class AlertConfigurationDslMarker

@AlertConfigurationDslMarker
class AlertConfigurationBuilder internal constructor() {
    private val notificationDefinitions = linkedMapOf<String, NotificationDefinition>()
    private val ruleDefinitions = mutableListOf<AlertRuleDefinition>()
    private val defaultNotificationIdsOverride = mutableListOf<String>()
    private var notificationDefaults = NotificationDefaults.DEFAULT

    fun addNotification(definition: NotificationDefinition): NotificationDefinition {
        registerNotification(definition)
        return definition
    }

    fun notification(definition: NotificationDefinition): NotificationDefinition =
        addNotification(definition)

    fun notifications(vararg definitions: NotificationDefinition) {
        definitions.forEach(::registerNotification)
    }

    fun notifications(definitions: Iterable<NotificationDefinition>) {
        definitions.forEach(::registerNotification)
    }

    fun notifications(block: NotificationConfigurationScope.() -> Unit) {
        NotificationConfigurationScope(::registerNotification).apply(block)
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
            defaultNotificationIdsOverride += id
        }
    }

    fun notificationDefaults(block: NotificationDefaultsBuilder.() -> Unit) {
        notificationDefaults = NotificationDefaultsBuilder(notificationDefaults).apply(block).build()
    }

    internal fun build(): AlertConfiguration {
        val definitions = notificationDefinitions.toMap()
        val defaults = LinkedHashSet<String>()
        if (defaultNotificationIdsOverride.isEmpty()) {
            defaults += definitions.keys
        } else {
            defaultNotificationIdsOverride.forEach { id ->
                require(definitions.containsKey(id)) { "Default notification '$id' is not defined" }
                defaults += id
            }
        }
        return AlertConfiguration(
            notifications = NotificationRegistry(definitions),
            rules = ruleDefinitions.toList(),
            defaultNotificationIds = defaults.toList(),
            notificationDefaults = notificationDefaults
        )
    }

    private fun registerNotification(definition: NotificationDefinition) {
        require(definition.id.isNotBlank()) { "Notification id must not be blank" }
        require(notificationDefinitions[definition.id] == null) { "Notification '${definition.id}' already defined" }
        notificationDefinitions[definition.id] = definition
    }
}

@AlertConfigurationDslMarker
class NotificationConfigurationScope internal constructor(
    private val register: (NotificationDefinition) -> Unit
) {
    fun addNotification(definition: NotificationDefinition): NotificationDefinition {
        register(definition)
        return definition
    }
}

fun alertConfiguration(block: AlertConfigurationBuilder.() -> Unit): AlertConfiguration =
    AlertConfigurationBuilder().apply(block).build()

data class NotificationDefaults(
    val repeatNotificationsEvery: Duration,
    val notifyOnFailures: Boolean
) {
    companion object {
        val DEFAULT = NotificationDefaults(
            repeatNotificationsEvery = 1.hours,
            notifyOnFailures = true
        )
    }
}

class NotificationDefaultsBuilder internal constructor(initial: NotificationDefaults) {
    var repeatNotificationsEvery: Duration = initial.repeatNotificationsEvery
    var notifyOnFailures: Boolean = initial.notifyOnFailures

    fun build(): NotificationDefaults = NotificationDefaults(
        repeatNotificationsEvery = repeatNotificationsEvery,
        notifyOnFailures = notifyOnFailures
    )
}
