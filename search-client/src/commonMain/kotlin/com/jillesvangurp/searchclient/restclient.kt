package com.jillesvangurp.searchclient

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*
import kotlin.random.Random

data class Node(val host: String, val port: Int)

interface NodeSelector {
    fun selectNode(nodes: Array<out Node>): Node
}

//fun createHttpClient(logging: Boolean): HttpClient {
//    return HttpClient(CIO) {
//        useDefaultTransformers = false // we want to handle responses directly
//        engine {
//            maxConnectionsCount = 100
//            endpoint {
//                keepAliveTime = 100_000
//                connectTimeout = 5_000
//                requestTimeout = 30_000
//                connectAttempts = 3
//            }
//        }
//    }
//}

expect fun defaultHttpClient(logging: Boolean = false): HttpClient

class RestClient(
    private val client: HttpClient = defaultHttpClient(),
    private val https: Boolean = false,
    private val user: String? = null,
    private val password: String? = null,
    // TODO smarter default node selector strategies to deal with node failure, failover, etc.
    private val nodeSelector: NodeSelector? = null,
    private vararg val nodes: Node
) {
    constructor(
        client: HttpClient,
        https: Boolean = false,
        user: String? = null,
        password: String? = null,
        host: String = "localhost",
        port: Int = 9200
    ) : this(
        client = client,
        https = https,
        user = user,
        password = password,
        nodeSelector = null,
        Node(host, port)
    )

    private fun nextNode(): Node = nodeSelector?.selectNode(nodes) ?: nodes[Random.nextInt(nodes.size)]

    suspend fun doRequest(
        pathComponents: List<String> = emptyList(),
        httpMethod: HttpMethod = HttpMethod.Post,
        parameters: Map<String, Any>? = null,
        payload: String? = null,
        contentType: ContentType = ContentType.Application.Json,
    ): RestResponse {

        val response = client.request {
            val node = nextNode()
            method = httpMethod
            url {
                host = node.host
                port = node.port
                if (!user.isNullOrBlank()) {
                    user = this@RestClient.user
                }
                if (!password.isNullOrBlank()) {
                    password = this@RestClient.password
                }
                protocol = if (https) URLProtocol.HTTPS else URLProtocol.HTTP
                path("/${pathComponents.joinToString("/")}")
                if (!parameters.isNullOrEmpty()) {
                    parameters.entries.forEach { (key, value) ->
                        parameter(key, value)
                    }
                }
                if (payload != null) {
                    setBody(TextContent(payload, contentType = contentType))
                }
            }
        }

        val responseBody = response.readBytes()
        return when (response.status) {
            HttpStatusCode.OK -> RestResponse.Status2XX.OK(responseBody)
            HttpStatusCode.Accepted -> RestResponse.Status2XX.Accepted(responseBody)
            HttpStatusCode.NotModified -> RestResponse.Status2XX.NotModified(responseBody)
            HttpStatusCode.PermanentRedirect -> RestResponse.Status3XX.PermanentRedirect(
                responseBody,
                response.headers["Location"]
            )
            HttpStatusCode.TemporaryRedirect -> RestResponse.Status3XX.TemporaryRedirect(
                responseBody,
                response.headers["Location"]
            )
            HttpStatusCode.Gone -> RestResponse.Status2XX.Gone(responseBody)
            HttpStatusCode.BadRequest -> RestResponse.Status4XX.BadRequest(responseBody)
            HttpStatusCode.Unauthorized -> RestResponse.Status4XX.UnAuthorized(responseBody)
            HttpStatusCode.Forbidden -> RestResponse.Status4XX.Forbidden(responseBody)
            HttpStatusCode.NotFound -> RestResponse.Status4XX.NotFound(responseBody)
            HttpStatusCode.InternalServerError -> RestResponse.Status5xx.InternalServerError(responseBody)
            HttpStatusCode.GatewayTimeout -> RestResponse.Status5xx.GatewayTimeout(responseBody)
            HttpStatusCode.ServiceUnavailable -> RestResponse.Status5xx.ServiceUnavailable(responseBody)
            else -> RestResponse.UnexpectedStatus(responseBody, response.status.value)
        }
    }
}

sealed class RestResponse() {
    enum class ResponseCategory {
        Success,
        RequestIsWrong,
        ServerProblem,
        Other
    }

    abstract val bytes: ByteArray
    abstract val responseCategory: ResponseCategory

    val completedNormally by lazy { responseCategory == ResponseCategory.Success }
    val text by lazy { bytes.decodeToString() }

    abstract class Status2XX(override val responseCategory: ResponseCategory = ResponseCategory.Success) :
        RestResponse() {
        class OK(override val bytes: ByteArray) : Status2XX()
        class NotModified(override val bytes: ByteArray) : Status2XX()
        class Accepted(override val bytes: ByteArray) : Status2XX()
        class Gone(override val bytes: ByteArray) : Status2XX()
    }

    abstract class Status3XX(override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse() {
        abstract val location: String?

        class PermanentRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX()

        class TemporaryRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX()
    }

    abstract class Status4XX(override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse() {
        class BadRequest(override val bytes: ByteArray) : Status4XX()
        class NotFound(override val bytes: ByteArray) : Status4XX()
        class UnAuthorized(override val bytes: ByteArray) : Status4XX()
        class Forbidden(override val bytes: ByteArray) : Status4XX()
    }

    abstract class Status5xx(override val responseCategory: ResponseCategory = ResponseCategory.ServerProblem) :
        RestResponse() {
        class InternalServerError(override val bytes: ByteArray) : Status5xx()
        class ServiceUnavailable(override val bytes: ByteArray) : Status5xx()
        class GatewayTimeout(override val bytes: ByteArray) : Status5xx()
    }

    class UnexpectedStatus(
        override val bytes: ByteArray, val statusCode: Int,
        override val responseCategory: ResponseCategory = ResponseCategory.Other
    ) : RestResponse()
}
