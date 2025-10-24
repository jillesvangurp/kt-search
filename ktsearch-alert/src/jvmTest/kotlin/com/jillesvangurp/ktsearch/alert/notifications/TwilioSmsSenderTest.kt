package com.jillesvangurp.ktsearch.alert.notifications

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class TwilioSmsSenderTest {
    @Test
    fun `should send form payload for each recipient`() = runBlocking {
        val captured = mutableListOf<FormDataContent>()
        val headers = mutableListOf<Map<String, String>>()

        val engine = MockEngine { request ->
            captured += request.body as FormDataContent
            headers += request.headers.entries().associate { it.key to it.value.first() }
            respond(
                content = "{}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine)
        val sender = TwilioSmsSender(
            httpClient = client,
            config = TwilioConfig(
                accountSid = "AC123",
                authToken = "secret",
                defaultSenderId = "+155501"
            )
        )
        sender.send(
            SmsMessage(
                senderId = null,
                recipients = listOf("+15550001", "+15550002"),
                body = "hello world"
            )
        )

        captured.shouldHaveSize(2)
        headers.forEach { headerMap ->
            headerMap[HttpHeaders.Authorization]?.startsWith("Basic ") shouldBe true
        }

        val first = captured.first().formData
        first["To"] shouldBe "+15550001"
        first["From"] shouldBe "+155501"
        first["Body"] shouldBe "hello world"

        val second = captured[1].formData
        second["To"] shouldBe "+15550002"
        Unit
    }

    @Test
    fun `should throw retryable exception on transient error`() = runBlocking {
        val engine = MockEngine { _ ->
            respond(
                content = "error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine)
        val sender = TwilioSmsSender(
            httpClient = client,
            config = TwilioConfig(
                accountSid = "AC123",
                authToken = "secret",
                defaultSenderId = "+155501"
            )
        )

        shouldThrow<RetryableTwilioException> {
            sender.send(
                SmsMessage(
                    senderId = null,
                    recipients = listOf("+15550001"),
                    body = "hello world"
                )
            )
        }
        Unit
    }

}
