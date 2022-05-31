package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SearchResponse(
    val took: Long,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val hits: Hits?,
    val aggs: JsonObject?
) {
    @Serializable
    data class Hit(
        @SerialName("_index")
        val index: String,
        @SerialName("_type")
        val type: String,
        @SerialName("_id")
        val id: String,
        @SerialName("_score")
        val score: Double,
        @SerialName("_source")
        val source: JsonObject?,
        val fields: JsonObject?,
    )

    @Serializable
    data class Hits(
        @SerialName("max_score")
        val maxScore: Double?,
        val total: Total,
        val hits: List<Hit>
    ) {
        @Serializable
        enum class TotalRelation {
            @SerialName("eq")
            Eq,
            @SerialName("gte")
            Gte
        }
        @Serializable
        data class Total(val value: Long, val relation: TotalRelation)
    }
}

val SearchResponse.total get() = this.hits?.total?.value ?: 0

suspend fun SearchClient.search(
    target: String,
    extraParameters: Map<String,String>?=null,
    block: SearchDSL.() -> Unit
): SearchResponse {
    val dsl = SearchDSL()
    block.invoke(dsl)
    return search(target = target, dsl = dsl, extraParameters = extraParameters)
}

suspend fun SearchClient.search(
    target: String,
    dsl: SearchDSL,
    extraParameters: Map<String,String>?=null,
    ) =
    search(target = target, rawJson = dsl.json(), extraParameters = extraParameters)


suspend fun SearchClient.search(
    target: String?,
    rawJson: String?,
    extraParameters: Map<String,String>?=null,
    ): SearchResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_search").toTypedArray())
        parameters(extraParameters)
        if (!rawJson.isNullOrBlank()) {
            rawBody(rawJson)
        }
    }.parse(SearchResponse.serializer(), json)
}