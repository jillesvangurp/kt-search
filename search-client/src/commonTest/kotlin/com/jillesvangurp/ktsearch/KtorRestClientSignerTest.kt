package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test

class KtorRestClientSignerTest {

    @Test
    fun shouldMergeSignedHeadersIntoOutgoingRequest() = coRun {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK
            )
        }
        val restClient = KtorRestClient(
            host = "search-example.us-west-2.es.amazonaws.com",
            port = 443,
            https = true,
            requestSigner = RequestSigner {
                mapOf(
                    "Authorization" to "SIG",
                    "X-Amz-Date" to "20240102T030405Z"
                )
            },
            client = HttpClient(engine)
        )

        restClient.doRequest(
            pathComponents = listOf("_cluster", "health"),
            httpMethod = HttpMethod.Get,
            headers = mapOf("X-Test" to "123")
        )

        captured?.headers?.get("Authorization") shouldBe "SIG"
        captured?.headers?.get("X-Amz-Date") shouldBe "20240102T030405Z"
        captured?.headers?.get("X-Test") shouldBe "123"
    }
}
