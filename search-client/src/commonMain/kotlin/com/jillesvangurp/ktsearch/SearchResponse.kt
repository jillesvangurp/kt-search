package com.jillesvangurp.ktsearch

import kotlinx.serialization.*
import kotlinx.serialization.json.*

typealias Aggregations = JsonObject

@Suppress("unused")
@Serializable
data class SearchResponse(
    val took: Long,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val hits: Hits?,
    // parse JsonObject to more specialized classes as needed/available and fall back to picking the JsonObject apart
    val aggregations: Aggregations?,
    @SerialName("_scroll_id")
    val scrollId: String?,
    @SerialName("pit_id")
    val pitId: String?
)  {
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
        val innerHits: Map<String, HitsContainer>?,
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
    return this.source?.parse<T>(json = json)
}

inline fun <reified T> JsonObject.parse(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(this)

val SearchResponse.ids get() = this.hits?.hits?.map { it.id } ?: listOf()
val SearchResponse.total get() = this.hits?.total?.value ?: 0

interface BucketAggregationResult<T> {
    val buckets: List<JsonObject>
}

inline fun <reified T> BucketAggregationResult<T>.decodeBuckets(json: Json = DEFAULT_JSON) = buckets.map { json.decodeFromJsonElement<T>(it) }

@Serializable
data class TermsBucket(
    val key: String,
    @SerialName("doc_count")
    val docCount: Long,
)

@Serializable
data class TermsAggregationResult(
    @SerialName("doc_count_error_upper_bound")
    val docCountErrorUpperBound: Long,
    @SerialName("sum_other_doc_count")
    val sumOtherDocCount: Long,
    override val buckets: List<JsonObject>
) : BucketAggregationResult<TermsBucket>

fun List<TermsBucket>.counts() = this.associate { it.key to it.docCount }

fun Aggregations?.termsResult(name: String, json: Json = DEFAULT_JSON): TermsAggregationResult =
    // nullability here would be annoying; better to just throw an exception
    this?.get(name)?.let { json.decodeFromJsonElement(TermsAggregationResult.serializer(), it) } ?: error("no such agg $name")

@Serializable
data class DateHistogramBucket(
    val key: Long,
    @SerialName("key_as_string")
    val keyAsString: String,
    @SerialName("doc_count")
    val docCount: Long,
    )

@Serializable
data class DateHistogramAggregationResult(
    override val buckets: List<JsonObject>
) : BucketAggregationResult<DateHistogramBucket>
inline fun <reified T> Aggregations?.getAggResult(
    name: String,
    json: Json = DEFAULT_JSON
): T = this?.get(name)?.let { json.decodeFromJsonElement(it) } ?: error("no such agg $name")

fun Aggregations?.dateHistogramResult(name: String, json: Json = DEFAULT_JSON): DateHistogramAggregationResult =
    getAggResult(name, json)

@Serializable
data class NumericAggregationResult(
    val value: Double,
    @SerialName("value_as_string")
    val valueAsString: String?,
)

fun Aggregations?.minResult(name: String,json: Json = DEFAULT_JSON): NumericAggregationResult =
    getAggResult(name, json)
fun Aggregations?.maxResult(name: String,json: Json = DEFAULT_JSON): NumericAggregationResult =
    getAggResult(name, json)
fun Aggregations?.bucketScriptResult(name: String,json: Json = DEFAULT_JSON): NumericAggregationResult =
    getAggResult(name, json)

@Serializable
data class ExtendedStatsBucketResult(
    val count: Int,
    val min: Double,
    val max: Double,
    val avg: Double,
    val sum: Double,
    @SerialName("sum_of_squares")
    val sumOfSquares: Double,
    val variance: Double,
    @SerialName("variance_population")
    val variancePopulation: Double,
    @SerialName("variance_sampling")
    val varianceSampling: Double,
    @SerialName("std_deviation")
    val stdDeviation: Double,
    @SerialName("std_deviation_population")
    val stdDeviationPopulation: Double,
    @SerialName("std_deviation_sampling")
    val stdDeviationSampling: Double,
    @SerialName("std_deviation_bounds")
    val stdDeviationBounds: Bounds
) {
    @Serializable
    data class Bounds(
        val upper: Double,
        val lower: Double,
        @SerialName("upper_population")
        val upperPopulation: Double,
        @SerialName("lower_population")
        val lowerPopulation: Double,
        @SerialName("upper_sampling")
        val upperSampling: Double,
        @SerialName("lower_sampling")
        val lowerSampling: Double,
    )
}

fun Aggregations?.extendedStatsBucketResult(name: String, json: Json = DEFAULT_JSON): ExtendedStatsBucketResult =
    getAggResult(name, json)
