package com.jillesvangurp.ktsearch.alert.notifications

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class NotificationDispatcherFactoryTest {
    @Test
    fun `should create dispatcher with provided handlers`() {
        val dispatcher = createNotificationDispatcher(
            emailSender = object : EmailSender {
                override suspend fun send(message: EmailMessage) = Unit
            },
            slackSender = object : SlackSender {
                override suspend fun send(config: SlackNotificationConfig, message: SlackMessage) = Unit
            },
            smsSenders = listOf(object : SmsSender {
                override val provider: String = "mock"
                override suspend fun send(message: SmsMessage) = Unit
            }),
            config = NotificationDispatcherConfig(includeConsole = false)
        )

        dispatcher.hasHandler(NotificationChannel.EMAIL) shouldBe true
        dispatcher.hasHandler(NotificationChannel.SLACK) shouldBe true
        dispatcher.hasHandler(NotificationChannel.SMS) shouldBe true
        dispatcher.hasHandler(NotificationChannel.CONSOLE) shouldBe false
    }
}
