@file:Suppress("unused")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.withJsonDsl
import kotlin.reflect.KProperty

open class AggQuery(name: String) : ESQuery(name)

inline fun <reified T : AggQuery> JsonDsl.add(name: String, aggQuery: T) {
    this[name] = aggQuery
}

private fun SearchDSL.aggs(): JsonDsl {
    return (this["aggs"]?.let { it as JsonDsl } ?: JsonDsl()).also {
        this["aggs"] = it
    }
}

private fun AggQuery.aggs(): JsonDsl {
    return (this["aggs"]?.let { it as JsonDsl } ?: JsonDsl()).also {
        this["aggs"] = it
    }
}

fun SearchDSL.agg(name: String, aggQuery: AggQuery, block: (AggQuery.() -> Unit)? = null) {
    aggs().add(name, aggQuery)
    block?.invoke(aggQuery)
}

fun AggQuery.agg(name: String, aggQuery: AggQuery, block: (AggQuery.() -> Unit)? = null) {
    aggs().add(name, aggQuery)
    block?.invoke(aggQuery)
}

fun SearchDSL.agg(name: Enum<*>, aggQuery: AggQuery, block: (AggQuery.() -> Unit)? = null) =
    agg(name.name, aggQuery, block)

fun AggQuery.agg(name: Enum<*>, aggQuery: AggQuery, block: (AggQuery.() -> Unit)? = null) =
    agg(name.name, aggQuery, block)

class TermsAggConfig : JsonDsl() {
    var field by property<String>()
    var aggSize by property<Long>("size") // can't redefine Map.size sadly
    var minDocCount by property<Long>("min_doc_count")
    var shardSize by property<Long>("shard_size")
    var showTermDocCountError by property<Long>("show_term_doc_count_error")

    /** include values by regex or exact valye; use the includePartition function for partitions. */
    var include by property<List<String>>()

    /** exclude values by regex or exact valye */
    var exclude by property<List<String>>()

    /**
     * Partitions the keys into the specified [numPartitions] and only returns keys falling in [partition].
     * Use for large terms aggregations to get results with multiple aggregation requests to avoid stressing with huge
     * responses.
     *
     * Note, cannot be used with an `exclude` clause.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/current/search-aggregations-bucket-terms-aggregation.html#_filtering_values_with_partitions
     * Elasticsearch 8.x only
     */
    fun includePartition(numPartitions: Int, partition: Int) {
        this["include"] = withJsonDsl {
            this["num_partitions"] = numPartitions
            this["partition"] = partition
        }
    }


    fun orderByKey(direction: SortOrder) {
        getOrCreateMutableList("order").add(
            withJsonDsl {
                this["_key"] = direction.name.lowercase()
            }
        )
    }

    fun orderByField(field: String, direction: SortOrder) {
        getOrCreateMutableList("order").add(
            withJsonDsl {
                this[field] = direction.name.lowercase()
            }
        )
    }

    fun orderByField(field: KProperty<*>, direction: SortOrder) {
        getOrCreateMutableList("order").add(
            withJsonDsl {
                this[field.name] = direction.name.lowercase()
            }
        )
    }

}

class TermsAgg(val field: String, block: (TermsAggConfig.() -> Unit)? = null) : AggQuery("terms") {
    constructor(field: KProperty<*>, block: (TermsAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = TermsAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class CompositeAggConfig : JsonDsl() {
    var aggSize by property<Int>("size")

    fun afterKey(afterKey: Map<String, *>) {
        this["after"] = afterKey
    }

    fun afterKey(vararg keyValues: Pair<String, Any?>) = afterKey(mapOf(*keyValues))

    fun afterKey(afterKey: JsonDsl) {
        this["after"] = afterKey
    }

    private fun addSource(name: String, block: JsonDsl.() -> Unit) {
        val source = withJsonDsl {
            this[name] = withJsonDsl {
                block.invoke(this)
            }
        }
        getOrCreateMutableList("sources").add(source)
    }

    fun source(name: String, block: JsonDsl.() -> Unit) = addSource(name, block)

    fun termsSource(
        name: String,
        field: String,
        order: SortOrder? = null,
        missingBucket: Boolean? = null
    ) = addSource(name) {
        this["terms"] = withJsonDsl {
            this["field"] = field
            order?.let { this["order"] = it.name.lowercase() }
            missingBucket?.let { this["missing_bucket"] = it }
        }
    }

    fun termsSource(
        name: String,
        field: KProperty<*>,
        order: SortOrder? = null,
        missingBucket: Boolean? = null
    ) = termsSource(name, field.name, order, missingBucket)

    fun histogramSource(
        name: String,
        field: String,
        interval: Number,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
        offset: Number? = null
    ) = addSource(name) {
        this["histogram"] = withJsonDsl {
            this["field"] = field
            this["interval"] = interval
            offset?.let { this["offset"] = it }
            order?.let { this["order"] = it.name.lowercase() }
            missingBucket?.let { this["missing_bucket"] = it }
        }
    }

    fun histogramSource(
        name: String,
        field: KProperty<*>,
        interval: Number,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
        offset: Number? = null
    ) = histogramSource(name, field.name, interval, order, missingBucket, offset)

    fun dateHistogramSource(
        name: String,
        field: String,
        calendarInterval: String? = null,
        fixedInterval: String? = null,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
        timeZone: String? = null,
        offset: String? = null
    ) = addSource(name) {
        this["date_histogram"] = withJsonDsl {
            this["field"] = field
            calendarInterval?.let { this["calendar_interval"] = it }
            fixedInterval?.let { this["fixed_interval"] = it }
            order?.let { this["order"] = it.name.lowercase() }
            missingBucket?.let { this["missing_bucket"] = it }
            timeZone?.let { this["time_zone"] = it }
            offset?.let { this["offset"] = it }
        }
    }

    fun dateHistogramSource(
        name: String,
        field: KProperty<*>,
        calendarInterval: String? = null,
        fixedInterval: String? = null,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
        timeZone: String? = null,
        offset: String? = null
    ) = dateHistogramSource(name, field.name, calendarInterval, fixedInterval, order, missingBucket, timeZone, offset)

    fun geoTileGridSource(
        name: String,
        field: String,
        precision: Int,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
    ) = addSource(name) {
        this["geotile_grid"] = withJsonDsl {
            this["field"] = field
            this["precision"] = precision
            order?.let { this["order"] = it.name.lowercase() }
            missingBucket?.let { this["missing_bucket"] = it }
        }
    }

    fun geoTileGridSource(
        name: String,
        field: KProperty<*>,
        precision: Int,
        order: SortOrder? = null,
        missingBucket: Boolean? = null,
    ) = geoTileGridSource(name, field.name, precision, order, missingBucket)
}

class CompositeAgg(block: (CompositeAggConfig.() -> Unit)? = null) : AggQuery("composite") {
    init {
        val config = CompositeAggConfig()
        block?.invoke(config)
        put(name, config)
    }
}

class AggRange : JsonDsl() {
    var key by property<String>()

    /**
     * Aggregation includes the `from` value
     */
    var from by property<Double>()

    /**
     * Aggregation excludes the `to` value
     */
    var to by property<Double>()

    companion object {
        fun create(block: AggRange.() -> Unit) = AggRange().apply(block)
    }
}

class RangesAggConfig : JsonDsl() {
    var field by property<String>()
    var ranges by property<List<AggRange>>()
}

class RangesAgg(val field: String, block: (RangesAggConfig.() -> Unit)? = null) : AggQuery("range") {
    constructor(field: KProperty<*>, block: (RangesAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = RangesAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class AggDateRange : JsonDsl() {
    /**
     * Customizes the key for each range
     */
    var key by property<String>()

    /**
     * Aggregation includes the `from` value
     */
    var from by property<String>()

    /**
     * Aggregation excludes the `to` value
     */
    var to by property<String>()

    companion object {
        fun create(block: AggDateRange.() -> Unit) = AggDateRange().apply(block)
    }
}

class DateRangesAggConfig : JsonDsl() {
    var field by property<String>()

    /**
     * Defines how documents that are missing a value should be treated.
     * By default, they will be ignored, but it is also possible to treat them as if they had a value.
     */
    var missing by property<String>()

    /**
     *  Specifies a date format by which the from and to response fields will be returned.
     */
    var format by property<String>()

    /**
     * Time zones may either be specified as an ISO 8601 UTC offset (e.g. +01:00 or -08:00)
     * or as one of the time zone ids from the TZ database.
     */
    var timeZone by property<String>()

    var ranges by property<List<AggDateRange>>()
}

class DateRangesAgg(val field: String, block: (DateRangesAggConfig.() -> Unit)? = null) : AggQuery("date_range") {
    constructor(field: KProperty<*>, block: (DateRangesAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = DateRangesAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class DateHistogramAggConfig : JsonDsl() {
    var field by property<String>()
    var calendarInterval by property<String>("calendar_interval") // can't redefine Map.size sadly
    var minDocCount by property<Long>("min_doc_count")
    var format by property<String>()
    var timeZone by property<String>("time_zone")
    var offset by property<String>()
    var missing by property<String>()
    var keyed by property<Boolean>()

    fun extendedBounds(min: String?=null, max: String?=null) {
        if(!min.isNullOrBlank() || !max.isNullOrBlank() ) {
            this["extended_bounds"] = withJsonDsl {
                this["min"] = min
                this["max"] = max
            }
        }
    }
}

class DateHistogramAgg(val field: String, block: (DateHistogramAggConfig.() -> Unit)? = null) :
    AggQuery("date_histogram") {

    constructor(field: KProperty<*>, block: (DateHistogramAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = DateHistogramAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class ExtendedStatsBucketAggConfig : JsonDsl() {
    var bucketsPath by property<String>("buckets_path")
    var gapPolicy by property<String>("gap_policy")
}

class ExtendedStatsBucketAgg(
    block: (ExtendedStatsBucketAggConfig.() -> Unit)? = null
) : AggQuery("extended_stats_bucket") {
    init {
        val config = ExtendedStatsBucketAggConfig()
        block?.invoke(config)
        put(name, config)
    }
}

class BucketsPath(block: BucketsPath.() -> Unit) : JsonDsl() {
    init {
        block()
    }
}

class BucketScriptAggConfig : JsonDsl() {
    var script by property<String>()
    var bucketsPath by property<BucketsPath>("buckets_path")
}

class BucketScriptAgg(
    block: (BucketScriptAggConfig.() -> Unit)? = null
) : AggQuery("bucket_script") {
    init {
        val config = BucketScriptAggConfig()
        block?.invoke(config)
        put(name, config)
    }
}

class BucketSortAggConfig : JsonDsl() {
    var aggSize by property<Long>("size")
    var sort by property<List<SortField>>()

    fun sort(block: SortBuilder.() -> Unit) {
        val builder = SortBuilder()
        block.invoke(builder)
        this["sort"] = builder.sortFields
    }
}

class BucketSortAgg(
    block: (BucketSortAggConfig.() -> Unit)? = null
) : AggQuery("bucket_sort") {
    init {
        val config = BucketSortAggConfig()
        block?.invoke(config)
        put(name, config)
    }
}

class CardinalityAggConfig : JsonDsl() {
    var field by property<String>()
}

class CardinalityAgg(
    val field: String,
    block: (CardinalityAggConfig.() -> Unit)? = null
) : AggQuery("cardinality") {
    constructor(field: KProperty<*>, block: (CardinalityAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = CardinalityAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class MaxAggConfig : JsonDsl() {
    var field by property<String>()
}

class MaxAgg(
    val field: String,
    block: (MaxAggConfig.() -> Unit)? = null
) : AggQuery("max") {
    constructor(field: KProperty<*>, block: (MaxAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = MaxAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class MinAggConfig : JsonDsl() {
    var field by property<String>()
}

class MinAgg(
    val field: String,
    block: (MinAggConfig.() -> Unit)? = null
) : AggQuery("min") {
    constructor(field: KProperty<*>, block: (MinAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = MinAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class TopHitsAggConfig : JsonDsl() {
    // can't redefine Map.size sadly
    var resultSize by property<Long>("size")
    var from by property<Long>()
    var sort by property<List<SortField>>()
    var explain by property<Boolean>()

    fun sort(block: SortBuilder.() -> Unit) {
        val builder = SortBuilder()
        block.invoke(builder)
        this["sort"] = builder.sortFields
    }
}

fun TopHitsAggConfig.filterSource(returnSource: Boolean) {
    this["_source"] = returnSource
}

fun TopHitsAggConfig.filterSource(vararg fields: KProperty<*>) {
    this["_source"] = arrayOf(*fields.map { it.name }.toTypedArray())
}

fun TopHitsAggConfig.filterSource(vararg fields: String) {
    this["_source"] = arrayOf(*fields)
}

fun TopHitsAggConfig.filterSource(block: SourceBuilder.() -> Unit) {
    val builder = SourceBuilder()
    block.invoke(builder)
    this["_source"] = builder.sourceFilter
}

fun TopHitsAggConfig.highlight(
    vararg fields: HighlightField,
    block: (Highlight.() -> Unit)?=null
) {
    val builder = Highlight()
    if(fields.isNotEmpty()) {
        builder.fields(*fields)
    }
    block?.invoke(builder)
    this["highlight"] = builder
}

class TopHitsAgg(block: (TopHitsAggConfig.() -> Unit)? = null) : AggQuery("top_hits") {
    init {
        val config = TopHitsAggConfig()
        block?.invoke(config)
        put(name, config)
    }
}

class FilterConfig : JsonDsl() {
    var query: ESQuery
        get() {
            @Suppress("UNCHECKED_CAST") // somehow needed
            val map = this["filter"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["query"] = value.wrapWithName()
        }

    fun namedFilter(name: String, query: ESQuery) {
        val filters = this["filters"]?.let { it as JsonDsl } ?: JsonDsl()
        this["filters"] = filters
        filters[name] = query.wrapWithName()
    }
}

class FiltersAgg(
    block: FilterConfig.() -> Unit
) : AggQuery("filters") {
    init {
        this[name] = FilterConfig().apply(block)
    }
}

class FilterAgg(filter: ESQuery) : AggQuery("filter") {
    init {
        this[name] = filter.wrapWithName()
    }
}

class SumAggConfig : JsonDsl() {
    var field by property<String>()
}

class SumAgg(val field: String, block: (SumAggConfig.() -> Unit)? = null) : AggQuery("sum") {
    constructor(field: KProperty<*>, block: (SumAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = SumAggConfig()
        config.field = field
        block?.invoke(config)
        put(name, config)
    }
}

class PercentilesAggConfig : JsonDsl() {
    var field by property<String>()
    var percentileValues by property<List<Double>>("values")
    var keyed by property<Boolean>()

    fun hdr(numberOfSignificantValueDigits: Int? = null, block: (JsonDsl.() -> Unit)? = null) {
        this["hdr"] = withJsonDsl {
            numberOfSignificantValueDigits?.let { this["number_of_significant_value_digits"] = it }
            block?.invoke(this)
        }
    }

    fun tdigest(compression: Number? = null, block: (JsonDsl.() -> Unit)? = null) {
        this["tdigest"] = withJsonDsl {
            compression?.let { this["compression"] = it }
            block?.invoke(this)
        }
    }
}

class PercentilesAgg(
    val field: String,
    block: (PercentilesAggConfig.() -> Unit)? = null
) : AggQuery("percentiles") {
    constructor(field: KProperty<*>, block: (PercentilesAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = PercentilesAggConfig()
        config.field = field
        config.keyed = true
        block?.invoke(config)
        put(name, config)
    }
}

class PercentileRanksAggConfig : JsonDsl() {
    var field by property<String>()
    var rankValues by property<List<Double>>("values")
    var keyed by property<Boolean>()

    fun hdr(numberOfSignificantValueDigits: Int? = null, block: (JsonDsl.() -> Unit)? = null) {
        this["hdr"] = withJsonDsl {
            numberOfSignificantValueDigits?.let { this["number_of_significant_value_digits"] = it }
            block?.invoke(this)
        }
    }

    fun tdigest(compression: Number? = null, block: (JsonDsl.() -> Unit)? = null) {
        this["tdigest"] = withJsonDsl {
            compression?.let { this["compression"] = it }
            block?.invoke(this)
        }
    }
}

class PercentileRanksAgg(
    val field: String,
    block: (PercentileRanksAggConfig.() -> Unit)? = null
) : AggQuery("percentile_ranks") {
    constructor(field: KProperty<*>, block: (PercentileRanksAggConfig.() -> Unit)? = null) : this(field.name, block)

    init {
        val config = PercentileRanksAggConfig()
        config.field = field
        config.keyed = true
        block?.invoke(config)
        put(name, config)
    }
}

class GeoTileGridAggConfig: JsonDsl() {
    var field by property<String>()
    /**
     * Zoom level (0..22). Corresponds to the
     */
    var precision by property<Int>()
    var aggSize by property<Long>("size")
    var shardSize by property<Long>()

    fun bounds(boundingBoxQueryConfig: GeoBoundingBoxQueryConfig.() -> Unit) {
        this["bounds"]= GeoBoundingBoxQueryConfig().apply(boundingBoxQueryConfig)
    }
}

class GeoTileGridAgg(field: String, precision: Int, block: (GeoTileGridAggConfig.() ->Unit)?=null): AggQuery("geotile_grid") {
    init {
        val config = GeoTileGridAggConfig()
        config.field = field
        config.precision = precision
        block?.let { config.apply(it)}
        put(name, config)
    }
}


class GeoCentroidAgg(field: String): AggQuery("geo_centroid") {
    init {
        put(name, withJsonDsl {
            this["field"] = field
        })
    }
}
