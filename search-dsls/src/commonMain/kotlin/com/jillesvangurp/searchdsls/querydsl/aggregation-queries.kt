@file:Suppress("unused")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

open class AggQuery(name: String) : ESQuery(name)
inline fun <reified T : AggQuery> JsonDsl.add(name: String, aggQuery: T) {
    this[name] = aggQuery
}

private fun SearchDSL.aggs(): JsonDsl {
    return this["aggs"]?.let { it as JsonDsl } ?: JsonDsl().also {
        this["aggs"] = it
    }
}

private fun AggQuery.aggs(): JsonDsl {
    return this["aggs"]?.let { it as JsonDsl } ?: JsonDsl().also {
        this["aggs"] = it
    }
}

fun SearchDSL.agg(name: String, aggQuery: AggQuery, block: (AggQuery.() -> Unit)?=null) {
    aggs().add(name, aggQuery)
    block?.invoke(aggQuery)
}

fun AggQuery.agg(name: String, aggQuery: AggQuery,block: (AggQuery.() -> Unit)?=null) {
    aggs().add(name, aggQuery)
    block?.invoke(aggQuery)
}

class TermsAggConfig : JsonDsl() {
    var field by property<String>()
    var aggSize by property<Long>("size") // can't redefine Map.size sadly
    var minSize by property<Long>("min_size")
    var shardSize by property<Long>("shard_size")
    var showTermDocCountError by property<Long>("show_term_doc_count_error")
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

class DateHistogramAggConfig : JsonDsl() {
    var field by property<String>()
    var calendarInterval by property<String>("calendar_interval") // can't redefine Map.size sadly
    var minDocCount by property<Long>("min_doc_count")
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
    var min by property<String>()
    var max by property<String>()

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



