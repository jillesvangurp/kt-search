@file:Suppress("unused")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import kotlin.reflect.KProperty

open class AggQuery(name: String) : ESQuery(name)
private fun JsonDsl.aggs(): JsonDsl {
    return this["aggs"]?.let { it as JsonDsl } ?: JsonDsl().also {
        this["aggs"]=it
    }
}

private fun AggQuery.aggs(): JsonDsl {
    return this["aggs"]?.let { it as JsonDsl } ?: JsonDsl().also {
        this["aggs"]=it
    }
}

fun SearchDSL.agg(name:String, aggQuery: AggQuery) {
   aggs()[name] = aggQuery
}

fun AggQuery.agg(name:String, aggQuery: AggQuery) {
    aggs()[name] = aggQuery
}

class TermsAggConfig: JsonDsl() {
    var field by property<String>()
    var aggSize by property<Long>("size") // can't redefine Map.size sadly
    var minSize by property<Long>("min_size")
    var shardSize by property<Long>("shard_size")
    var showTermDocCountError by property<Long>("show_term_doc_count_error")
}

class TermsAgg(val field: String, block: (TermsAggConfig.() -> Unit)?=null): AggQuery("terms") {
    constructor(field: KProperty<*>,block: (TermsAggConfig.() -> Unit)?=null) : this(field.name, block)
    init {
        val config=TermsAggConfig()
        config.field=field
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

