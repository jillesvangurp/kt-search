package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationRegistry
import com.jillesvangurp.ktsearch.alert.notifications.NotificationsDsl
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.alert.rules.AlertRulesDsl
import com.jillesvangurp.ktsearch.alert.rules.alertRules

data class AlertConfiguration(
    val notifications: NotificationRegistry,
    val rules: List<AlertRuleDefinition>
)

@DslMarker
annotation class AlertConfigurationDslMarker

@AlertConfigurationDslMarker
class AlertConfigurationBuilder internal constructor() {
    private val notificationBlocks = mutableListOf<NotificationsDsl.() -> Unit>()
    private val notificationDefinitions = mutableListOf<NotificationDefinition>()
    private val ruleDefinitions = mutableListOf<AlertRuleDefinition>()

    fun notifications(block: NotificationsDsl.() -> Unit) {
        notificationBlocks += block
    }

    fun notification(definition: NotificationDefinition) {
        notificationDefinitions += definition
    }

    fun rules(block: AlertRulesDsl.() -> Unit) {
        ruleDefinitions += alertRules(block)
    }

    internal fun build(): AlertConfiguration {
        val notificationsDsl = NotificationsDsl()
        notificationBlocks.forEach { notificationsDsl.apply(it) }
        notificationDefinitions.forEach { notificationsDsl.definition(it) }
        val registry = notificationsDsl.build()
        return AlertConfiguration(
            notifications = registry,
            rules = ruleDefinitions.toList()
        )
    }
}

fun alertConfiguration(block: AlertConfigurationBuilder.() -> Unit): AlertConfiguration =
    AlertConfigurationBuilder().apply(block).build()
