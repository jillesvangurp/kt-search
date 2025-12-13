@file:Suppress("unused")
@file:OptIn(ExperimentalSerializationApi::class)

package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.ModelSerializationStrategy
import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

typealias Aggregations = JsonObject
typealias MatchedQueries = JsonElement

@Suppress("unused")
@Serializable
data class SearchResponse(
    val took: Long?, // sometimes missing; apparently
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
    val pitId: String?,
    @SerialName("point_in_time_id")
    val pointInTimeId: String?,
    val suggest: Map<String, List<Suggest>>?,
) {
    @Serializable
    data class Suggest(
        val text: String,
        val offset: Int,
        val length: Int,
        val options: List<Option>
    ) {
        @Serializable
        data class Option(
            val text: String,
            val score: Double,
            val freq: Int?,
            @SerialName("collate_match")
            val collateMatch: Boolean?,
            val highlighted: String?
        )
    }

    @Serializable
    data class Hit(
        @SerialName("_index")
        val index: String,
        @SerialName("_id")
        override val id: String,
        @SerialName("_score")
        val score: Double?,
        @SerialName("_source")
        override val source: JsonObject?,
        val fields: JsonObject?,
        val sort: JsonArray?,
        @SerialName("inner_hits")
        val innerHits: Map<String, HitsContainer>?,
        val highlight: JsonObject?,
        @SerialName("_seq_no")
        override val seqNo: Long?,
        @SerialName("_primary_term")
        override val primaryTerm: Long?,
        @SerialName("_version")
        override val version: Long?,
        @SerialName("_explanation")
        val explanation: JsonObject?,
        /**
         * If named queries are used, the response includes a matched_queries property for each hit.
         * There are two forms of the matched_queries response:
         *   - if include_named_queries_score was set, response includes map with named query and score
         *   - otherwise just list of the named queries.
         *  so the field is just a JsonElement with 2 extension functions:
         *   - MatchedQueries::names()
         *   - MatchedQueries::scoreByName()
         */
        @SerialName("matched_queries")
        val matchedQueries: MatchedQueries?,
    ) : SourceInformation

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

/**
 * Quick way to get the document hits from a SearchResponse. You can override the default [json] if you need to.
 */
inline fun <reified T> SearchResponse.parseHits(json: Json = DEFAULT_JSON) = searchHits.map {
    it.parseHit<T>(json)
}

/**
 * Non reified version of [parseHits] that takes a [deserializationStrategy].
 */
fun <T> SearchResponse.parseHits(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON) =
    searchHits.map {
        it.parseHit(deserializationStrategy, json)
    }

/**
 * Version of [parseHits] that reuses the `ModelSerializationStrategy`.
 */
fun <T : Any> SearchResponse.parseHits(
    deserializationStrategy: ModelSerializationStrategy<T>,
) = searchHits.mapNotNull { hit ->
    hit.source?.let { deserializationStrategy.deSerialize(hit.source) }
}

inline fun <reified T> SearchResponse.Hit.parseHit(json: Json = DEFAULT_JSON): T {
    return this.source?.parse<T>(json = json) ?: error("no source found")
}

fun <T> SearchResponse.Hit.parseHit(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON): T {
    return this.source?.parse(deserializationStrategy, json = json) ?: error("no source found")
}

inline fun <reified T> Flow<SearchResponse.Hit>.parseHits(
): Flow<T> = map {
    it.parseHit<T>()
}

fun <T> Flow<SearchResponse.Hit>.parseHits(
    deserializationStrategy: DeserializationStrategy<T>,
    json: Json = DEFAULT_JSON
): Flow<T> = map {
    it.parseHit(deserializationStrategy, json)
}

fun <T : Any> Flow<SearchResponse.Hit>.parseHits(deserializationStrategy: ModelSerializationStrategy<T>): Flow<T> =
    mapNotNull { hit ->
        hit.source?.let { deserializationStrategy.deSerialize(it) }
    }

inline fun <reified T> JsonObject.parse(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(this)

fun <T> JsonObject.parse(deserializationStrategy: DeserializationStrategy<T>, json: Json = DEFAULT_JSON) =
    json.decodeFromJsonElement(deserializationStrategy, this)

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
    @SerialName("key_as_string")
    val keyAsString: String?,
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
data class RangesBucket(
    val key: String,
    @SerialName("doc_count")
    val docCount: Long,
    // Range Aggregation on Integer field returns Double
    // https://github.com/elastic/elasticsearch/issues/43258
    val from: Double?,
    val to: Double?
)

@Serializable
data class RangesAggregationResult(
    override val buckets: List<JsonObject>
) : BucketAggregationResult<RangesBucket>

val RangesAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, RangesBucket.serializer()) }

// JVM cannot distinguish List type parameter in the runtime, so we cannot use "counts" name
// as for the TermsBucket
fun List<RangesBucket>.rangeCounts() = this.associate { it.key to it.docCount }

fun Aggregations?.rangesResult(name: String, json: Json = DEFAULT_JSON): RangesAggregationResult =
    getAggResult(name, json)

fun Aggregations?.rangesResult(name: Enum<*>, json: Json = DEFAULT_JSON): RangesAggregationResult =
    getAggResult(name, json)

@Serializable
data class DateRangesBucket(
    val key: String,
    @SerialName("doc_count")
    val docCount: Long,
    val from: Double?,
    @SerialName("from_as_string")
    val fromAsString: String?,
    val to: Double?,
    @SerialName("to_as_string")
    val toAsString: String?
)

@Serializable
data class DateRangesAggregationResult(
    override val buckets: List<JsonObject>
) : BucketAggregationResult<DateRangesBucket>

val DateRangesAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, DateRangesBucket.serializer()) }

fun List<DateRangesBucket>.dateRangeCounts() = this.associate { it.key to it.docCount }

fun Aggregations?.dateRangesResult(name: String, json: Json = DEFAULT_JSON): DateRangesAggregationResult =
    getAggResult(name, json)

fun Aggregations?.dateRangesResult(name: Enum<*>, json: Json = DEFAULT_JSON): DateRangesAggregationResult =
    getAggResult(name, json)

@Serializable
data class FilterAggregationResult(
    @SerialName("doc_count")
    val docCount: Long,
)

fun Aggregations?.filterResult(name: String): FilterBucket? =
    this?.get(name)?.let {
        FilterBucket(
            name = name,
            docCount = it.jsonObject["doc_count"]?.jsonPrimitive?.long ?: 0,
            bucket = it.jsonObject
        )
    }


data class FilterBucket(
    val name: String,
    @SerialName("doc_count")
    val docCount: Long,
    val bucket: JsonObject
)

@Serializable
data class FiltersAggregationResult(
    val buckets: JsonObject
)

fun FiltersAggregationResult.bucket(name: String, json: Json): FilterBucket? =
    buckets[name]?.let { json.decodeFromJsonElement(it) }

val FiltersAggregationResult.namedBuckets
    get() = buckets.map { (n, e) ->
        FilterBucket(
            name = n,
            docCount = e.jsonObject["doc_count"]?.jsonPrimitive?.long ?: 0,
            bucket = e.jsonObject
        )
    }


fun Aggregations?.filtersResult(name: String, json: Json = DEFAULT_JSON): FiltersAggregationResult =
    getAggResult(name, json)

fun Aggregations?.filtersResult(name: Enum<*>, json: Json = DEFAULT_JSON): FiltersAggregationResult =
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
): T = // nullability here would be annoying; better to just throw an exception
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
    @EncodeDefault
    val count: Int = 0,
    @EncodeDefault
    val min: Double = 0.0,
    @EncodeDefault
    val max: Double = 0.0,
    @EncodeDefault
    val avg: Double = 0.0,
    @EncodeDefault
    val sum: Double = 0.0,
    @SerialName("sum_of_squares")
    @EncodeDefault
    val sumOfSquares: Double = 0.0,
    @EncodeDefault
    val variance: Double = 0.0,
    @SerialName("variance_population")
    @EncodeDefault
    val variancePopulation: Double = 0.0,
    @SerialName("variance_sampling")
    @EncodeDefault
    val varianceSampling: Double = 0.0,
    @SerialName("std_deviation")
    @EncodeDefault
    val stdDeviation: Double = 0.0,
    @SerialName("std_deviation_population")
    @EncodeDefault
    val stdDeviationPopulation: Double = 0.0,
    @SerialName("std_deviation_sampling")
    @EncodeDefault
    val stdDeviationSampling: Double = 0.0,
    @SerialName("std_deviation_bounds")
    @EncodeDefault
    val stdDeviationBounds: Bounds = Bounds()
) {
    @Serializable
    data class Bounds(
        @EncodeDefault
        val upper: Double = 0.0,
        @EncodeDefault
        val lower: Double = 0.0,
        @SerialName("upper_population")
        @EncodeDefault
        val upperPopulation: Double = 0.0,
        @SerialName("lower_population")
        @EncodeDefault
        val lowerPopulation: Double = 0.0,
        @SerialName("upper_sampling")
        @EncodeDefault
        val upperSampling: Double = 0.0,
        @SerialName("lower_sampling")
        @EncodeDefault
        val lowerSampling: Double = 0.0,
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

@Serializable
data class GeoTileGridBucket(
    val key: String,
    @SerialName("doc_count")
    val docCount: Long,
)

@Serializable
data class GeoTileGridResult(
    override val buckets: List<JsonObject>
) : BucketAggregationResult<GeoTileGridBucket>

val GeoTileGridResult.parsedBuckets get() = buckets.map { Bucket(it, GeoTileGridBucket.serializer()) }

fun Aggregations?.geoTileGridResult(name: String, json: Json = DEFAULT_JSON): GeoTileGridResult =
    getAggResult(name, json)

fun Aggregations?.geoTileGridResult(name: Enum<*>, json: Json = DEFAULT_JSON): GeoTileGridResult =
    getAggResult(name, json)

@Serializable
data class CompositeBucket(
    val key: JsonObject,
    @SerialName("doc_count")
    val docCount: Long
)

@Serializable
data class CompositeAggregationResult(
    @SerialName("after_key")
    val afterKey: JsonObject? = null,
    override val buckets: List<JsonObject>
) : BucketAggregationResult<CompositeBucket>

val CompositeAggregationResult.parsedBuckets get() = buckets.map { Bucket(it, CompositeBucket.serializer()) }

fun Aggregations?.compositeResult(name: String, json: Json = DEFAULT_JSON): CompositeAggregationResult =
    getAggResult(name, json)

fun Aggregations?.compositeResult(name: Enum<*>, json: Json = DEFAULT_JSON): CompositeAggregationResult =
    getAggResult(name, json)


@Serializable
data class GeoCentroidResult(
    val location: Point,
    val count: Long,

    ) {
    val pointCoordinate get() = doubleArrayOf(location.lon, location.lat)

    @Serializable
    data class Point(val lat: Double, val lon: Double)
}

fun Aggregations?.geoCentroid(name: String) = getAggResult<GeoCentroidResult>(name)
fun Aggregations?.geoCentroid(name: Enum<*>) = getAggResult<GeoCentroidResult>(name)

@Serializable
data class SumAggregationResult(
    val value: Double,
)

fun Aggregations?.sumAggregationResult(name: String, json: Json = DEFAULT_JSON): SumAggregationResult =
    getAggResult(name, json)

fun Aggregations?.sumAggregationResult(name: Enum<*>, json: Json = DEFAULT_JSON): TopHitsAggregationResult =
    getAggResult(name.name, json)

/**
 * If include_named_queries_score parameter is not `true` only ES returns only query names for each Hit.
 * If include_named_queries_score is `true` please use the `scoreByName` function.
 */
fun MatchedQueries?.names(json: Json = DEFAULT_JSON): List<String> =
    this?.let { json.decodeFromJsonElement(it) } ?: emptyList()

/**
 * The request parameter named include_named_queries_score controls whether scores associated
 * with the matched queries are returned or not. When set, the response includes a matched_queries map
 * that contains the name of the query that matched as a key and its associated score as the value.
 */
fun MatchedQueries?.scoreByName(json: Json = DEFAULT_JSON): Map<String, Double> =
    this?.let { json.decodeFromJsonElement(it) } ?: emptyMap()
