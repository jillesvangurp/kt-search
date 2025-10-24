package com.jillesvangurp.ktsearch.alert.notifications

import com.jillesvangurp.ktsearch.alert.testutil.coRun
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Instant

private val sampleContext = NotificationContext(
    ruleId = "rule-1",
    ruleName = "Test Rule",
    triggeredAt = Instant.parse("2024-01-01T00:00:00Z"),
    matchCount = 2,
    matches = listOf(buildJsonObject { })
)

class NotificationHandlersTest {
    @Test
    fun `should render slack message with variables`() = coRun {
        val config = SlackNotificationConfig(
            webhookUrl = "https://hooks.slack.com/services/ABC",
            channelName = "#alerts",
            username = "AlertBot",
            message = "Rule {{ruleName}} found {{matchCount}} issues"
        )
        val definition = NotificationDefinition(
            id = "slack-alert",
            config = config,
            defaultVariables = mapOf("extra" to "value")
        )
        val sender = FakeSlackSender()
        val handler = SlackNotificationHandler(sender)
        handler.send(definition, mapOf("ruleName" to "Overridden", "matchCount" to "2"), sampleContext)

        sender.captured shouldBe SlackMessage(
            text = "Rule Overridden found 2 issues",
            channel = "#alerts",
            username = "AlertBot"
        )
        sender.config shouldBe config
    }

    @Test
    fun `should route sms to matching provider`() = coRun {
        val config = SmsNotificationConfig(
            provider = "mock",
            senderId = "alerts",
            to = listOf("+1234567890", "+1987654321"),
            message = "Rule {{ruleName}} -> {{matchCount}}"
        )
        val definition = NotificationDefinition(
            id = "sms-alert",
            config = config
        )
        val sender = FakeSmsSender("mock")
        val handler = SmsNotificationHandler(listOf(sender))
        handler.send(definition, mapOf("ruleName" to "Test Rule", "matchCount" to "5"), sampleContext)

        sender.messages.shouldContainExactly(
            SmsMessage(senderId = "alerts", recipients = listOf("+1234567890", "+1987654321"), body = "Rule Test Rule -> 5")
        )
    }
}

private class FakeSlackSender : SlackSender {
    var captured: SlackMessage? = null
    var config: SlackNotificationConfig? = null

    override suspend fun send(config: SlackNotificationConfig, message: SlackMessage) {
        this.captured = message
        this.config = config
    }
}

private class FakeSmsSender(override val provider: String) : SmsSender {
    val messages = mutableListOf<SmsMessage>()
    override suspend fun send(message: SmsMessage) {
        messages += message
    }
}
