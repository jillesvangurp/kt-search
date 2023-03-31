package com.jillesvangurp.ktsearch

import kotlinx.datetime.Instant
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.reflect.KProperty

typealias Aggregations = JsonObject

@Suppress("unused")
@Serializable
data class SearchResponse(
    val took: Long,
    @SerialName("_shards")
    val shards: Shards?,
    @SerialName("timed_out")
    val timedOut: Boolean?,
    val hits: Hits?,
    // parse JsonObject to more specialized classes as needed/available and fall back to picking the JsonObject apart
    val aggregations: Aggregations?,
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
        val innerHits: Map<String, HitsContainer>?,
        val highlight: JsonObject?,
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

fun <T> SearchResponse.parseHits(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON) = searchHits.map {
    it.parseHit(deserializationStrategy,json)
}

inline fun <reified T> SearchResponse.Hit.parseHit(json: Json = DEFAULT_JSON): T {
    return this.source?.parse<T>(json = json) ?: error("no source found")
}

fun <T> SearchResponse.Hit.parseHit(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON): T {
    return this.source?.parse(deserializationStrategy,json = json) ?: error("no source found")
}

inline fun <reified T> JsonObject.parse(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(this)

fun <T> JsonObject.parse(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON) = json.decodeFromJsonElement(deserializationStrategy,this)

fun <T> JsonObject.parse(json: Json = DEFAULT_JSON, deserializationStrategy: DeserializationStrategy<T>) =
    json.decodeFromJsonElement(deserializationStrategy, this)

val SearchResponse.ids get() = this.hits?.hits?.map { it.id } ?: listOf()
val SearchResponse.total get() = this.hits?.total?.value ?: 0

interface BucketAggregationResult<T> {
    val buckets: List<JsonObject>
}

class Bucket<T>(
    val aggregations: Aggregations,
    private val deserializationStrategy: DeserializationStrategy<T>,
    private val json: Json = DEFAULT_JSON
) {
    val parsed by lazy { aggregations.parse(json, deserializationStrategy) }
}

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

val TermsAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, TermsBucket.serializer()) }

fun List<TermsBucket>.counts() = this.associate { it.key to it.docCount }

fun Aggregations?.termsResult(name: String, json: Json = DEFAULT_JSON): TermsAggregationResult =
    getAggResult(name, json)
fun Aggregations?.termsResult(name: Enum<*>, json: Json = DEFAULT_JSON): TermsAggregationResult =
    getAggResult(name, json)
@Serializable
data class DateHistogramBucket(
    val key: Long,
    @SerialName("key_as_string")
    val keyAsString: String,
    @SerialName("doc_count")
    val docCount: Long,
)

val DateHistogramBucket.keyAsInstant get() = Instant.fromEpochMilliseconds(key)

@Serializable
data class DateHistogramAggregationResult(
    override val buckets: List<JsonObject>
) : BucketAggregationResult<DateHistogramBucket>

inline fun <reified T> Aggregations?.getAggResult(
    name: String,
    json: Json = DEFAULT_JSON
): T = // nullability here would be annoying; better to just throw an exception
    this?.get(name)?.let { json.decodeFromJsonElement(it) } ?: error("no such agg $name")

inline fun <reified T> Aggregations?.getAggResult(
    name: Enum<*>,
    json: Json = DEFAULT_JSON
) : T = // nullability here would be annoying; better to just throw an exception
    this?.get(name.name)?.let { json.decodeFromJsonElement(it) } ?: error("no such agg $name")

fun Aggregations?.dateHistogramResult(name: String, json: Json = DEFAULT_JSON): DateHistogramAggregationResult =
    getAggResult(name, json)
fun Aggregations?.dateHistogramResult(name: Enum<*>, json: Json = DEFAULT_JSON): DateHistogramAggregationResult =
    getAggResult(name, json)

val DateHistogramAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, DateHistogramBucket.serializer()) }

@Serializable
data class DoubleValueAggregationResult(
    val value: Double,
    @SerialName("value_as_string")
    val valueAsString: String?,
)

@Serializable
data class LongValueAggregationResult(
    val value: Long,
    @SerialName("value_as_string")
    val valueAsString: String?,
)

fun Aggregations?.minResult(name: String, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
    getAggResult(name, json)
fun Aggregations?.minResult(name: Enum<*>, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
    getAggResult(name, json)

fun Aggregations?.maxResult(name: String, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
    getAggResult(name, json)

fun Aggregations?.maxResult(name: Enum<*>, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
    getAggResult(name, json)

fun Aggregations?.cardinalityResult(name: String, json: Json = DEFAULT_JSON): LongValueAggregationResult =
    getAggResult(name, json)

fun Aggregations?.cardinalityResult(name: Enum<*>, json: Json = DEFAULT_JSON): LongValueAggregationResult =
    getAggResult(name, json)

fun Aggregations?.bucketScriptResult(name: String, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
    getAggResult(name, json)
fun Aggregations?.bucketScriptResult(name: Enum<*>, json: Json = DEFAULT_JSON): DoubleValueAggregationResult =
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

fun Aggregations?.extendedStatsBucketResult(name: Enum<*>, json: Json = DEFAULT_JSON): ExtendedStatsBucketResult =
    getAggResult(name, json)

@Serializable
data class TopHitsAggregationResult(
    val hits: SearchResponse.Hits,
)
fun Aggregations?.topHitResult(name: String, json: Json = DEFAULT_JSON): TopHitsAggregationResult =
    getAggResult(name, json)

fun Aggregations?.topHitResult(name: Enum<*>, json: Json = DEFAULT_JSON): TopHitsAggregationResult =
    getAggResult(name.name, json)

