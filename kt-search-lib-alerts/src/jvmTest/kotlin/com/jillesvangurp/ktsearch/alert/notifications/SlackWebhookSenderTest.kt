package com.jillesvangurp.ktsearch.alert.notifications

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class SlackWebhookSenderTest {
    @Test
    fun `should post json payload`() = runBlocking {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as TextContent).text
            respond(
                content = "ok",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, listOf(ContentType.Application.Json.toString()))
            )
        }
        val client = HttpClient(engine)
        val sender = SlackWebhookSender(client)
        sender.send(
            config = SlackNotificationConfig(
                webhookUrl = "https://hooks.slack.com/services/test",
                channelName = "#alerts",
                username = "Alertbot",
                message = "Hello {{name}}!"
            ),
            message = SlackMessage(
                text = "Hello World!",
                channel = "#alerts",
                username = "Alertbot"
            )
        )

        val json = Json.parseToJsonElement(checkNotNull(capturedBody)).jsonObject
        json["text"]!!.jsonPrimitive.content shouldBe "Hello World!"
        json["channel"]!!.jsonPrimitive.content shouldBe "#alerts"
        json["username"]!!.jsonPrimitive.content shouldBe "Alertbot"
        Unit
    }

    @Test
    fun `should throw SlackWebhookException on non success status`() = runBlocking {
        val engine = MockEngine { request ->
            respond(
                content = "error",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, listOf(ContentType.Application.Json.toString()))
            )
        }
        val client = HttpClient(engine)
        val sender = SlackWebhookSender(client)
        shouldThrow<SlackWebhookException> {
            sender.send(
                config = SlackNotificationConfig(
                    webhookUrl = "https://hooks.slack.com/services/test",
                    channelName = null,
                    username = null,
                    message = "ignored"
                ),
                message = SlackMessage(
                    text = "fail",
                    channel = null,
                    username = null
                )
            )
        }
        Unit
    }
}
