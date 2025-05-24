@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import kotlin.time.Duration

data class SearchAPIRequest(
    internal var body: String? = null,
    internal var contentType: String = "application/json",
    internal var pathComponents: List<String> = listOf(),
    internal val parameters: MutableMap<String, String> = mutableMapOf(),
    internal val headers: MutableMap<String, Any> = mutableMapOf()
) {
    fun path(vararg components: String?) {
        pathComponents = components.toList().filterNotNull()
    }

    fun parameter(key: String, value: String?) {
        value?.let {
            parameters[key] = value
        }
    }

    fun parameter(key: String, value: Duration?) {
        value.toElasticsearchTimeUnit()?.let {
            parameters[key] = it
        }
    }

    fun parameter(key: String, value: Number?) {
        value?.let {
            parameters[key] = "$value"
        }
    }

    fun parameter(key: String, value: Boolean?) {
        value?.let {
            parameters[key] = "$value"
        }
    }

    fun parameter(key: String, value: Enum<*>?) {
        value?.let {
            parameters[key] = value.snakeCase()
        }
    }

    fun parameters(params: Map<String, String>?) {
        params?.let {
            parameters.putAll(params)
        }
    }
    fun header(key: String, value: String) {
        headers[key] = value
    }
    fun header(key: String, values: List<String>) {
        headers[key] = values
    }

    fun json(dsl: JsonDsl, pretty: Boolean = false) {
        body = dsl.json(pretty)
    }

    fun rawBody(body: String, contentType: String = "application/json") {
        this.body = body
        this.contentType = contentType
    }
}

suspend fun RestClient.head(block: SearchAPIRequest.() -> Unit): RestResponse {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        httpMethod = HttpMethod.Head,
        parameters = request.parameters,
        headers = request.headers
    )
}

suspend fun RestClient.post(block: SearchAPIRequest.() -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf(request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Post,
        parameters = request.parameters,
        headers = request.headers,
        contentType= request.contentType,

    ).asResult()
}

suspend fun RestClient.delete(block: SearchAPIRequest.() -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Delete,
        parameters = request.parameters,
        headers = request.headers
    ).asResult()
}

suspend fun RestClient.get(block: SearchAPIRequest.() -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        httpMethod = HttpMethod.Get,
        parameters = request.parameters,
        headers = request.headers
    ).asResult()
}

suspend fun RestClient.put(block: SearchAPIRequest.() -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Put,
        parameters = request.parameters,
        headers = request.headers,
        contentType= request.contentType,
    ).asResult()
}
