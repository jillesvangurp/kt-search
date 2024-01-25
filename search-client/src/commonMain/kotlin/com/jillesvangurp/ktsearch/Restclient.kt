package com.jillesvangurp.ktsearch

import io.ktor.http.*
import io.ktor.utils.io.core.*

data class Node(
    val host: String,
    val port: Int?,
)
interface NodeSelector {
    suspend fun selectNode(): Node
}

/**
 * Minimalistic abstraction that should allow for different clients.
 *
 * For now, the [KtorRestClient] is the only implementation.
 */
interface RestClient : Closeable {
    suspend fun nextNode(): Node

    suspend fun doRequest(
        pathComponents: List<String> = emptyList(),
        httpMethod: HttpMethod = HttpMethod.Post,
        parameters: Map<String, Any>? = null,
        payload: String? = null,
        contentType: ContentType = ContentType.Application.Json,
        headers:Map<String,Any>?=null
    ): RestResponse
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
        class Created(override val bytes: ByteArray) : Status2XX(201)
        class Accepted(override val bytes: ByteArray) : Status2XX(202)
        class Gone(override val bytes: ByteArray) : Status2XX(204)
    }

    abstract class Status3XX(override val status: Int,override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse(status) {
        open val location: String? = null

        class PermanentRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX(301)

        class TemporaryRedirect(override val bytes: ByteArray, override val location: String?) :
            Status3XX(303)
        class NotModified(override val bytes: ByteArray) :
            Status3XX(304)
    }

    abstract class Status4XX(override val status: Int, val path: String,requestBody: String?, override val responseCategory: ResponseCategory = ResponseCategory.RequestIsWrong) :
        RestResponse(status) {
        class BadRequest(override val bytes: ByteArray,path: String,  requestBody: String?) : Status4XX(
            status = 400,
            path = path,
            requestBody = requestBody
        )
        class NotFound(override val bytes: ByteArray,path: String,  requestBody: String?) : Status4XX(
            status = 404,
            path = path,
            requestBody = requestBody
        )
        class UnAuthorized(override val bytes: ByteArray, path: String, requestBody: String?) : Status4XX(
            status = 401,
            path = path,
            requestBody = requestBody
        )
        class Forbidden(override val bytes: ByteArray, path: String, requestBody: String?) : Status4XX(
            status = 403,
            path = path,
            requestBody = requestBody
        )
        // usually means a circuit breaker kicked in
        class TooManyRequests(override val bytes: ByteArray, path: String,requestBody: String?) : Status4XX(
            status = 429,
            path = path,
            requestBody = requestBody
        )
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

class RestException(val response: RestResponse) : Exception("${response.responseCategory} ${response.status}: ${response.text}") {
    val status = response.status
}

fun RestResponse.asResult(): Result<RestResponse.Status2XX> {
    return if(this is RestResponse.Status2XX) {
        Result.success(this)
    } else {
        Result.failure(RestException(this))
    }
}