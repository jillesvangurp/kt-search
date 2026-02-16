package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

class OpenSearchSigV4Test {

    @Test
    fun shouldCanonicalizeRequestDeterministically() {
        val canonical = canonicalRequest(
            method = "GET",
            path = "/_cluster/health",
            queryParameters = mapOf(
                "level" to listOf("indices"),
                "wait_for_status" to listOf("yellow")
            ),
            headers = mapOf(
                "host" to "example.us-west-2.es.amazonaws.com",
                "x-amz-content-sha256" to "e3b0c44298fc1c149afbf4c8996fb924" +
                    "27ae41e4649b934ca495991b7852b855",
                "x-amz-date" to "20240102T030405Z"
            ),
            payloadHash = "e3b0c44298fc1c149afbf4c8996fb924" +
                "27ae41e4649b934ca495991b7852b855"
        )

        canonical shouldBe """
            GET
            /_cluster/health
            level=indices&wait_for_status=yellow
            host:example.us-west-2.es.amazonaws.com
            x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
            x-amz-date:20240102T030405Z

            host;x-amz-content-sha256;x-amz-date
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        """.trimIndent()
    }

    @Test
    fun shouldBuildStringToSignDeterministically() {
        val canonical = canonicalRequest(
            method = "GET",
            path = "/",
            queryParameters = emptyMap(),
            headers = mapOf(
                "host" to "search.example.com",
                "x-amz-content-sha256" to sha256Hex(""),
                "x-amz-date" to "20240102T030405Z"
            ),
            payloadHash = sha256Hex("")
        )
        val stringToSign = sigV4StringToSign(
            timestamp = "20240102T030405Z",
            scope = "20240102/us-west-2/es/aws4_request",
            canonicalRequest = canonical
        )

        stringToSign shouldBe """
            AWS4-HMAC-SHA256
            20240102T030405Z
            20240102/us-west-2/es/aws4_request
            b72dc8d5af569d78b72223a35e04f69626a514b9d35b9727d554b60841652adf
        """.trimIndent()
    }

    @Test
    fun shouldIncludeExpectedAuthorizationParts() = coRun {
        val signer = AwsSigV4Signer(
            AwsSigV4Config(
                region = "us-west-2",
                credentialsProvider = AwsCredentialsProvider {
                    AwsCredentials("AKIDEXAMPLE", "secret")
                },
                clock = FixedClock("2024-01-02T03:04:05Z")
            )
        )
        val headers = signer.sign(
            SigningRequest(
                node = Node("search-example.us-west-2.es.amazonaws.com", 443),
                https = true,
                path = "/_cluster/health",
                method = "GET",
                queryParameters = emptyMap()
            )
        )

        headers["Authorization"] shouldContain
            "Credential=AKIDEXAMPLE/20240102/us-west-2/es/aws4_request"
        headers["Authorization"] shouldContain
            "SignedHeaders=host;x-amz-content-sha256;x-amz-date"
    }

    @Test
    fun shouldIncludeSessionTokenHeaderWhenConfigured() = coRun {
        val signer = AwsSigV4Signer(
            AwsSigV4Config(
                region = "eu-west-1",
                credentialsProvider = AwsCredentialsProvider {
                    AwsCredentials(
                        accessKeyId = "AKIDEXAMPLE",
                        secretAccessKey = "secret",
                        sessionToken = "token-123"
                    )
                },
                clock = FixedClock("2024-01-02T03:04:05Z")
            )
        )
        val headers = signer.sign(
            SigningRequest(
                node = Node("search-example.eu-west-1.es.amazonaws.com", 443),
                https = true,
                path = "/_search",
                method = "POST",
                queryParameters = emptyMap(),
                payload = "{}"
            )
        )

        headers["X-Amz-Security-Token"] shouldBe "token-123"
    }

    @Test
    fun shouldHashPayloadForEmptyAndNonEmptyBodies() {
        sha256Hex("") shouldBe
            "e3b0c44298fc1c149afbf4c8996fb924" +
            "27ae41e4649b934ca495991b7852b855"
        sha256Hex("{}") shouldBe
            "44136fa355b3678a1146ad16f7e8649e" +
            "94fb4fc21fe77e8310c060f61caaff8a"
    }

    @Test
    fun shouldAutoDetectServiceNameFromHost() {
        resolveService(null, "abc.us-east-1.aoss.amazonaws.com") shouldBe "aoss"
        resolveService(null, "abc.us-east-1.es.amazonaws.com") shouldBe "es"
        resolveService("es", "abc.us-east-1.aoss.amazonaws.com") shouldBe "es"
    }

    private class FixedClock(isoInstant: String) : Clock {
        private val instant = Instant.parse(isoInstant)
        override fun now(): Instant = instant
    }
}
