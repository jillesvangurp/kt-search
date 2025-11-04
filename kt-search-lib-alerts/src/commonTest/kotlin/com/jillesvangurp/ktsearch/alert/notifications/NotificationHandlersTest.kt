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
        val sender = RecordingSlackSender()
        val definition = slackNotification(
            id = "slack-alert",
            sender = sender,
            webhookUrl = "https://hooks.slack.com/services/ABC",
            message = "Rule {{ruleName}} found {{matchCount}} issues",
            channelName = "#alerts",
            username = "AlertBot",
            defaultVariables = mapOf("extra" to "value")
        )

        definition.dispatch(
            variables = mapOf(
                NotificationVariable.RULE_NAME.key to "Overridden",
                NotificationVariable.MATCH_COUNT.key to "2"
            ),
            context = sampleContext
        )

        sender.captured shouldBe SlackMessage(
            text = "Rule Overridden found 2 issues",
            channel = "#alerts",
            username = "AlertBot"
        )
        sender.config?.webhookUrl shouldBe "https://hooks.slack.com/services/ABC"
    }

    @Test
    fun `should send sms with rendered body`() = coRun {
        val sender = RecordingSmsSender()
        val definition = smsNotification(
            id = "sms-alert",
            sender = sender,
            to = listOf("+1234567890", "+1987654321"),
            message = "Rule {{ruleName}} -> {{matchCount}}",
            senderId = "alerts"
        )

        definition.dispatch(
            variables = mapOf(
                NotificationVariable.RULE_NAME.key to "Test Rule",
                NotificationVariable.MATCH_COUNT.key to "5"
            ),
            context = sampleContext
        )

        sender.messages.shouldContainExactly(
            SmsMessage(senderId = "alerts", recipients = listOf("+1234567890", "+1987654321"), body = "Rule Test Rule -> 5")
        )
    }

    @Test
    fun `should render email templates`() = coRun {
        val sender = RecordingEmailSender()
        val definition = emailNotification(
            id = "email-alert",
            sender = sender,
            from = "alerts@example.com",
            to = listOf("ops@example.com"),
            subject = "{{ruleName}} fired",
            body = "Found {{matchCount}} issues"
        )

        definition.dispatch(
            variables = mapOf(
                NotificationVariable.RULE_NAME.key to "Test Rule",
                NotificationVariable.MATCH_COUNT.key to "3"
            ),
            context = sampleContext
        )

        sender.messages.shouldContainExactly(
            EmailMessage(
                from = "alerts@example.com",
                to = listOf("ops@example.com"),
                subject = "Test Rule fired",
                body = "Found 3 issues",
                contentType = "text/plain",
                cc = emptyList(),
                bcc = emptyList()
            )
        )
    }
}

private class RecordingSlackSender : SlackSender {
    var captured: SlackMessage? = null
    var config: SlackNotificationConfig? = null

    override suspend fun send(config: SlackNotificationConfig, message: SlackMessage) {
        this.captured = message
        this.config = config
    }
}

private class RecordingSmsSender : SmsSender {
    val messages = mutableListOf<SmsMessage>()
    override suspend fun send(message: SmsMessage) {
        messages += message
    }
}

private class RecordingEmailSender : EmailSender {
    val messages = mutableListOf<EmailMessage>()
    override suspend fun send(message: EmailMessage) {
        messages += message
    }
}
