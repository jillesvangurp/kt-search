package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import io.ktor.http.*

data class SearchAPIRequest(
    internal var body: String? = null,
    internal var contentType: ContentType = ContentType.Application.Json,
    internal var pathComponents: List<String> = listOf(),
    internal val parameters: MutableMap<String, String> = mutableMapOf()
) {
    fun path(vararg components: String) {
        pathComponents = components.toList()
    }

    fun parameter(key: String, value: String) {
        parameters[key] = value
    }

    fun json(dsl: JsonDsl, pretty: Boolean = false) {
        body = dsl.json(pretty)
    }

    fun rawBody(body: String, contentType: ContentType = ContentType.Application.Json) {
        this.body = body
        this.contentType = contentType
    }
}

suspend fun RestClient.post(block: (SearchAPIRequest) -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf(request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Post,
        parameters = request.parameters
    ).asResult()
}

suspend fun RestClient.delete(block: (SearchAPIRequest) -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Delete,
        parameters = request.parameters
    ).asResult()
}

suspend fun RestClient.get(block: (SearchAPIRequest) -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
//        payload = request.body,
        httpMethod = HttpMethod.Get,
        parameters = request.parameters
    ).asResult()
}
suspend fun RestClient.put(block: (SearchAPIRequest) -> Unit): Result<RestResponse.Status2XX> {
    val request = SearchAPIRequest()
    block.invoke(request)
    return doRequest(
        pathComponents = listOf("/" + request.pathComponents.joinToString("/")),
        payload = request.body,
        httpMethod = HttpMethod.Put,
        parameters = request.parameters
    ).asResult()
}