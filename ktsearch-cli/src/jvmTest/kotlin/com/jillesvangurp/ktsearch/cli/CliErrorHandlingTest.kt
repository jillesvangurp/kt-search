package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.core.CliktError
import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.RestResponse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class CliErrorHandlingTest {
    private val connection = ConnectionOptions(
        host = "localhost",
        port = 9200,
        https = false,
        user = null,
        password = null,
        elasticApiKey = null,
        logging = false,
        awsSigV4 = false,
        awsRegion = null,
        awsService = null,
        awsProfile = null,
    )

    @Test
    fun mapsBadRequestToFriendlyCliError() {
        val rest = RestResponse.Status4XX.BadRequest(
            bytes = """{"error":"bad request"}""".encodeToByteArray(),
            path = "/products/_search",
            requestBody = "{}",
        )

        val mapped = mapCliException(RestException(rest), connection)

        (mapped is CliktError) shouldBe true
        mapped.message shouldContain "HTTP 400"
        mapped.message shouldContain "/products/_search"
    }

    @Test
    fun mapsNotFoundToFriendlyCliError() {
        val rest = RestResponse.Status4XX.NotFound(
            bytes = """{"error":"not found"}""".encodeToByteArray(),
            path = "/missing-index",
            requestBody = null,
        )

        val mapped = mapCliException(RestException(rest), connection)

        (mapped is CliktError) shouldBe true
        mapped.message shouldContain "HTTP 404"
        mapped.message shouldContain "/missing-index"
    }

    @Test
    fun mapsNetworkLikeFailuresToFriendlyCliError() {
        val mapped = mapCliException(
            Throwable("top", ConnectFailureException("connection refused")),
            connection,
        )

        (mapped is CliktError) shouldBe true
        mapped.message shouldContain "Unable to connect"
        mapped.message shouldContain "http://localhost:9200"
    }
}

private class ConnectFailureException(
    message: String,
) : Exception(message)
