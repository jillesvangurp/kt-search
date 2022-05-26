package com.jillesvangurp.ktsearch

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

expect fun defaultKtorHttpClient(logging: Boolean = false): HttpClient

interface RestClient {
    fun nextNode(): Node

    suspend fun doRequest(
        pathComponents: List<String> = emptyList(),
        httpMethod: HttpMethod = HttpMethod.Post,
        parameters: Map<String, Any>? = null,
        payload: String? = null,
        contentType: ContentType = ContentType.Application.Json,
    ): RestResponse
}

/**
 * Simple facade for different rest clients like ktor, okhttp, etc.
 */
class KtorRestClient(
    private val client: HttpClient = defaultKtorHttpClient(),
    private val https: Boolean = false,
    private val user: String? = null,
    private val password: String? = null,
    // TODO smarter default node selector strategies to deal with node failure, failover, etc.
    private val nodeSelector: NodeSelector? = null,
    private vararg val nodes: Node
) : RestClient {
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

    override fun nextNode(): Node = nodeSelector?.selectNode(nodes) ?: nodes[Random.nextInt(nodes.size)]

    override suspend fun doRequest(
        pathComponents: List<String>,
        httpMethod: HttpMethod,
        parameters: Map<String, Any>?,
        payload: String?,
        contentType: ContentType,
    ): RestResponse {

        val response = client.request {
            val node = nextNode()
            method = httpMethod
            url {
                host = node.host
                port = node.port
                if (!user.isNullOrBlank()) {
                    user = this@KtorRestClient.user
                }
                if (!password.isNullOrBlank()) {
                    password = this@KtorRestClient.password
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

class RestException(response: RestResponse) : Exception("${response.responseCategory} ${response.status}: ${response.text}")
fun RestResponse.asResult(): Result<RestResponse.Status2XX> {
    return if(this is RestResponse.Status2XX) {
        Result.success(this)
    } else {
        Result.failure(RestException(this))
    }
}

sealed class RestResponse(open val status: Int) {
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

    abstract class Status2XX(override val status: Int, override val responseCategory: ResponseCategory = ResponseCategory.Success) :
        RestResponse(status) {
        class OK(override val bytes: ByteArray) : Status2XX(200)
        class NotModified(override val bytes: ByteArray) : Status2XX(201)
        class Accepted(override val bytes: ByteArray) : Status2XX(202)
        class Gone(override val bytes: ByteArray) : Status2XX(204)
    }

    abstract class Status3XX(override val status: Int,override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse(status) {
        abstract val location: String?

        class PermanentRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX(301)

        class TemporaryRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX(303)
    }

    abstract class Status4XX(override val status: Int, override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse(status) {
        class BadRequest(override val bytes: ByteArray) : Status4XX(400)
        class NotFound(override val bytes: ByteArray) : Status4XX(404)
        class UnAuthorized(override val bytes: ByteArray) : Status4XX(401)
        class Forbidden(override val bytes: ByteArray) : Status4XX(403)
    }

    abstract class Status5xx(override val status: Int, override val responseCategory: ResponseCategory = ResponseCategory.ServerProblem) :
        RestResponse(status) {
        class InternalServerError(override val bytes: ByteArray) : Status5xx(500)
        class ServiceUnavailable(override val bytes: ByteArray) : Status5xx(503)
        class GatewayTimeout(override val bytes: ByteArray) : Status5xx(502)
    }

    class UnexpectedStatus(
        override val bytes: ByteArray, override val status:  Int,
        override val responseCategory: ResponseCategory = ResponseCategory.Other
    ) : RestResponse(status)
}
