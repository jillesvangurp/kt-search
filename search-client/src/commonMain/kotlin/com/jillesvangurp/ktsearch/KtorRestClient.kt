package com.jillesvangurp.ktsearch

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.content.*
import io.ktor.http.*

expect fun defaultKtorHttpClient(
    logging: Boolean = false,
    user: String? = null,
    password: String? = null
): HttpClient

/**
 * Ktor-client implementation of the RestClient.
 */
class KtorRestClient(
    private vararg val nodes: Node = arrayOf(Node("localhost", 9200)),
    private val https: Boolean = false,
    private val user: String? = null,
    private val password: String? = null,
    private val logging: Boolean = false,
    private val client: HttpClient = defaultKtorHttpClient(logging = logging, user = user, password = password),
    private val nodeSelector: NodeSelector = RoundRobinNodeSelector(nodes),
) : RestClient {
    constructor(
        host: String = "localhost",
        port: Int = 9200,
        https: Boolean = false,
        user: String? = null,
        password: String? = null,
        logging: Boolean = false,
        client: HttpClient = defaultKtorHttpClient(logging = logging, user = user, password = password),
    ) : this(
        client = client,
        https = https,
        user = user,
        password = password,
        logging = logging,
        nodes = arrayOf(Node(host, port))
    )

    override fun nextNode(): Node = nodeSelector.selectNode()

    @Suppress("UNCHECKED_CAST")
    override suspend fun doRequest(
        pathComponents: List<String>,
        httpMethod: HttpMethod,
        parameters: Map<String, Any>?,
        payload: String?,
        contentType: ContentType,
        headers: Map<String, Any>?
    ): RestResponse {

        val response = client.request {
            val node = nextNode()
            method = httpMethod
            url {
                host = node.host
                node.port?.let {
                    port = node.port
                }
                protocol = if (https) URLProtocol.HTTPS else URLProtocol.HTTP
                path(pathComponents.joinToString("/"))
                if (!parameters.isNullOrEmpty()) {
                    parameters.entries.forEach { (key, value) ->
                        parameter(key, value)
                    }
                }

            }
            headers?.let { providedHeaders ->
                headers {
                    providedHeaders.forEach {
                        val value = it.value
                        if (value is String) {
                            append(it.key, value)
                        } else if (value is List<*>) {
                            appendAll(it.key, value as List<String>)
                        }
                    }
                }
            }
            if (payload != null) {
                setBody(TextContent(payload, contentType = contentType))
            }
        }


        val responseBody = try {
            response.body<ByteArray>()
        } catch (e: IllegalStateException) {
            ByteArray(0)
        }

        return when (response.status) {
            HttpStatusCode.OK -> RestResponse.Status2XX.OK(responseBody)
            HttpStatusCode.Created -> RestResponse.Status2XX.Created(responseBody)
            HttpStatusCode.Accepted -> RestResponse.Status2XX.Accepted(responseBody)
            HttpStatusCode.Gone -> RestResponse.Status2XX.Gone(responseBody)
            HttpStatusCode.PermanentRedirect -> RestResponse.Status3XX.PermanentRedirect(
                responseBody,
                response.headers["Location"]
            )

            HttpStatusCode.TemporaryRedirect -> RestResponse.Status3XX.TemporaryRedirect(
                responseBody,
                response.headers["Location"]
            )

            HttpStatusCode.NotModified -> RestResponse.Status3XX.NotModified(
                responseBody
            )

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



