package com.jillesvangurp.ktsearch

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import io.ktor.utils.io.core.Closeable

expect fun defaultKtorHttpClient(
    logging: Boolean = false,
    user: String? = null,
    password: String? = null,
    elasticApiKey: String? = null,
    block: HttpClientConfig<*>.()->Unit
): HttpClient

fun HttpClientConfig<*>.defaultInit(
    logging: Boolean ,
    user: String? ,
    password: String? ,
    elasticApiKey: String?,

) {
    engine {
        pipelining = true
    }
    if(!user.isNullOrBlank() && !password.isNullOrBlank()) {
        install(Auth) {
            basic {
                credentials {
                    BasicAuthCredentials(user, password)
                }
                sendWithoutRequest {
                    true
                }
            }
        }
    }
    if(!elasticApiKey.isNullOrBlank()) {
        defaultRequest {
            header("Authorization","ApiKey $elasticApiKey")
        }
    }
    if (logging) {
        install(Logging) {
            level = LogLevel.ALL
        }
    } else {
        install(Logging) {
            level = LogLevel.NONE
        }
    }
}

/**
 * Ktor-client implementation of the RestClient.
 */
class KtorRestClient(
    private vararg val nodes: Node = arrayOf(
        Node(
            "localhost", 9200
        )
    ),
    private val https: Boolean = false,
    private val user: String? = null,
    private val password: String? = null,
    private val logging: Boolean = false,
    private val nodeSelector: NodeSelector = RoundRobinNodeSelector(nodes),
    private val elasticApiKey: String? = null,
    private val client: HttpClient = defaultKtorHttpClient(
        logging = logging,
        user = user,
        password = password,
        elasticApiKey = elasticApiKey
    ) {
        defaultInit(logging, user, password, elasticApiKey)
    },
) : RestClient, Closeable {
    constructor(
        host: String = "localhost",
        port: Int = 9200,
        https: Boolean = false,
        user: String? = null,
        password: String? = null,
        logging: Boolean = false,
        elasticApiKey: String? = null,
        client: HttpClient = defaultKtorHttpClient(
            logging = logging,
            user = user,
            password = password,
            elasticApiKey = elasticApiKey
        ) {
            defaultInit(logging, user, password, elasticApiKey)
        },
    ) : this(
        client = client,
        https = https,
        user = user,
        password = password,
        logging = logging,
        nodes = arrayOf(Node(host, port)),
        elasticApiKey = elasticApiKey
    )

    override suspend fun nextNode(): Node = nodeSelector.selectNode()

    @Suppress("UNCHECKED_CAST")
    override suspend fun doRequest(
        pathComponents: List<String>,
        httpMethod: com.jillesvangurp.ktsearch.HttpMethod,
        parameters: Map<String, Any>?,
        payload: String?,
        contentType: String,
        headers: Map<String, Any>?
    ): RestResponse {

        val response = client.request {
            val node = nextNode()
            method = HttpMethod.parse(httpMethod.value)
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
                setBody(TextContent(payload, contentType = ContentType.parse(contentType)))
            }
        }


        val responseBody = try {
            response.body<ByteArray>()
        } catch (_: IllegalStateException) {
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

            HttpStatusCode.BadRequest -> RestResponse.Status4XX.BadRequest(
                responseBody,
                pathComponents.joinToString("/"),
                payload
            )

            HttpStatusCode.Unauthorized -> RestResponse.Status4XX.UnAuthorized(
                responseBody,
                pathComponents.joinToString("/"),
                payload
            )

            HttpStatusCode.Forbidden -> RestResponse.Status4XX.Forbidden(
                responseBody,
                pathComponents.joinToString("/"),
                payload
            )

            HttpStatusCode.NotFound -> RestResponse.Status4XX.NotFound(
                responseBody,
                pathComponents.joinToString("/"),
                payload
            )

            HttpStatusCode.TooManyRequests -> RestResponse.Status4XX.TooManyRequests(
                responseBody,
                pathComponents.joinToString("/"),
                payload
            )

            HttpStatusCode.InternalServerError -> RestResponse.Status5xx.InternalServerError(responseBody)
            HttpStatusCode.GatewayTimeout -> RestResponse.Status5xx.GatewayTimeout(responseBody)
            HttpStatusCode.ServiceUnavailable -> RestResponse.Status5xx.ServiceUnavailable(responseBody)
            else -> RestResponse.UnexpectedStatus(responseBody, response.status.value)
        }
    }

    override fun close() {
        client.close()
    }
}



