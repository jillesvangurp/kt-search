package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.Collapse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class SearchResponse(
    val took: Long,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val hits: Hits?,
    // parse JsonObject to more specialized classes as needed/available and fall back to picking the JsonObject apart
    val aggregations: Map<String, JsonObject> = mapOf(),
    @SerialName("_scroll_id")
    val scrollId: String?,
    @SerialName("pit_id")
    val pitId: String?
) {
    @Serializable
    data class Hit(
        @SerialName("_index")
        val index: String,
        @SerialName("_type")
        val type: String?,
        @SerialName("_id")
        val id: String,
        @SerialName("_score")
        val score: Double?,
        @SerialName("_source")
        val source: JsonObject?,
        val fields: JsonObject?,
        val sort: JsonArray?,
        @SerialName("inner_hits")
        val innerHits: Map<String,HitsContainer>?,
    )

    @Serializable
    data class Hits(
        @SerialName("max_score")
        val maxScore: Double?,
        val total: Total?,
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

@Serializable
class HitsContainer(val hits: SearchResponse.Hits)

val SearchResponse.searchHits get() = this.hits?.hits ?: listOf()
inline fun <reified T> SearchResponse.parseHits(json: Json = DEFAULT_JSON) = searchHits.map {
    it.parseHit<T>(json)
}

inline fun <reified T> SearchResponse.Hit.parseHit(json: Json = DEFAULT_JSON): T? {
    return this.source?.parse<T>()
}

inline fun <reified T> JsonObject.parse(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(this)

val SearchResponse.ids get() = this.hits?.hits?.map { it.id } ?: listOf()
val SearchResponse.total get() = this.hits?.total?.value ?: 0

@Serializable
data class Bucket(
    val key: String,
    @SerialName("doc_count") val docCount: Long
)

@Serializable
data class BucketAggregationResult(
    @SerialName("doc_count_error_upper_bound")
    val docCountErrorUpperBound: Long,
    @SerialName("sum_other_doc_count")
    val sumOtherDocCount: Long,
    val buckets: List<Bucket>
)

fun List<Bucket>.counts() = this.associate { it.key to it.docCount }

fun JsonObject.asBucketAggregationResult() = DEFAULT_JSON.decodeFromJsonElement<BucketAggregationResult>(this)
