package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.alert.notifications.notification
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.searchdsls.querydsl.matchAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class AlertConfigurationDslTest {
    @Test
    fun `rules scope supports add and unary plus`() {
        val first = notification("first") {}
        val second = notification("second") {}

        val configuration = alertConfiguration {
            notifications {
                add(first)
                +second
            }
        }

        configuration.notifications.require("first") shouldBe first
        configuration.notifications.require("second") shouldBe second
    }

    @Test
    fun `rules scope can override default notification ids`() {
        val configuration = alertConfiguration {
            notifications {
                +notification("email") {}
                +notification("slack") {}
            }
            rules {
                defaultNotificationIds("slack")
                +AlertRuleDefinition.newRule(
                    name = "Example",
                    cronExpression = "* * * * *",
                    target = "logs-*",
                    notifications = emptyList()
                ) {
                    query = matchAll()
                }
            }
        }

        configuration.defaultNotificationIds shouldContainExactly listOf("slack")
    }

    @Test
    fun `rules scope can override notification defaults`() {
        val configuration = alertConfiguration {
            notifications {
                +notification("email") {}
            }
            rules {
                notificationDefaults {
                    notifyOnFailures = false
                    repeatNotificationsEvery = 5.minutes
                }
                +AlertRuleDefinition.newRule(
                    name = "Defaults Example",
                    cronExpression = "* * * * *",
                    target = "logs-*",
                    notifications = emptyList()
                ) {
                    query = matchAll()
                }
            }
        }

        configuration.notificationDefaults.notifyOnFailures shouldBe false
        configuration.notificationDefaults.repeatNotificationsEvery shouldBe 5.minutes
    }
}
