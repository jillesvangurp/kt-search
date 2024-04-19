package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Suppress("unused")
class AnalyzeRequest : JsonDsl() {
    var analyzer by property<String>()
    var attributes by property<List<String>>()
    var charFilter by property<List<String>>()
    var field by property<String>()
    var filter by property<List<String>>()
    var normalizer by property<String>()
    var text by property<List<String>>()
    var tokenizer by property<String>()
}


@Serializable
data class AnalyzeResponse(
    @SerialName("tokens") val tokens: List<AnalyzeToken>,
)

@Serializable
data class AnalyzeToken(
    @SerialName("token") val token: String,
    @SerialName("start_offset") val startOffset: Int,
    @SerialName("end_offset") val endOffset: Int,
    @SerialName("type") val type: String,
    @SerialName("position") val position: Int,
)

suspend fun SearchClient.analyze(
    target: String? = null,
    block: AnalyzeRequest.() -> Unit
): AnalyzeResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_analyze").toTypedArray())
        json(AnalyzeRequest().apply(block))
    }.parse(AnalyzeResponse.serializer())
}

