@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@DslMarker
annotation class SearchDSLMarker

@SearchDSLMarker
open class ESQuery(
    val name: String,
    val queryDetails: JsonDsl = JsonDsl()
) : IJsonDsl by queryDetails {

    fun wrapWithName(): Map<String, Any?> = dslObject { this[name] = queryDetails }

    override fun toString(): String {
        return wrapWithName().toString()
    }
}

@Suppress("UNCHECKED_CAST")
fun JsonDsl.esQueryProperty(): ReadWriteProperty<Any, ESQuery> {
    return object : ReadWriteProperty<Any, ESQuery> {
        override fun getValue(thisRef: Any, property: KProperty<*>): ESQuery {
            val map = this@esQueryProperty[property.name] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }

        override fun setValue(thisRef: Any, property: KProperty<*>, value: ESQuery) {
            this@esQueryProperty[property.name] = value.wrapWithName()
        }
    }
}

fun customQuery(name: String, block: JsonDsl.() -> Unit): ESQuery {
    val q = ESQuery(name)
    block.invoke(q.queryDetails)
    return q
}

class KnnQuery(
    field: String,
    queryVector: List<Double>,
    k: Int = 10,
    numCandidates: Int = k,
    block: (JsonDsl.() -> Unit)? = null
) : JsonDsl() {
    init {
        this["field"] = field
        this["query_vector"] = queryVector
        this["k"] = k
        this["num_candidates"] = numCandidates
        block?.let { this.apply(it) }
    }

    constructor(
        field: KProperty<*>,
        queryVector: List<Double>,
        k: Int = 10,
        numCandidates: Int = k,
        block: (JsonDsl.() -> Unit)? = null
    ) :
            this(
                field = field.name,
                queryVector = queryVector,
                k = k,
                numCandidates = numCandidates,
                block = block
            )
}

@Suppress("UNCHECKED_CAST")
class SearchDSL : JsonDsl(), QueryClauses {
    var from: Int by property()
    var trackTotalHits: String by property()
    var seqNoPrimaryTerm: Boolean by property()
    var version: Boolean by property()

    /** Same as the size property on Elasticsearch. But as kotlin's map already has a size property, we can't use that name. */
    var resultSize: Int by property("size") // clashes with Map.size

    // Elasticsearch has this object in a object kind of thing that we need to compensate for.
    var query: ESQuery
        get() {
            val map = this["query"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["query"] = value.wrapWithName()
        }

    var postFilter: ESQuery
        get() {
            val map = this["post_filter"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["post_filter"] = value.wrapWithName()
        }

    var knn by property<KnnQuery>()
    var aggs: JsonDsl
        get() = this["aggs"] as JsonDsl
        set(value) {
            this["aggs"] = value
        }
}

enum class SortOrder { ASC, DESC }
enum class SortMode { MIN, MAX, SUM, AVG, MEDIAN }

@Suppress("UNUSED_PARAMETER")
class SortField(field: String, order: SortOrder? = null, mode: SortMode? = null)

class SortBuilder {
    private val _sortFields = mutableListOf<Any>()

    val sortFields get() = _sortFields.toList()

    operator fun String.unaryPlus() = _sortFields.add(this)
    operator fun KProperty<*>.unaryPlus() = _sortFields.add(this)

    fun add(
        field: KProperty<*>,
        order: SortOrder = SortOrder.DESC,
        mode: SortMode? = null,
        missing: String? = null,
        block: (JsonDsl.() -> Unit)? = null
    ) =
        add(field.name, order, mode, missing, block)

    fun add(
        field: String,
        order: SortOrder = SortOrder.DESC,
        mode: SortMode? = null,
        missing: String? = null,
        block: (JsonDsl.() -> Unit)? = null
    ) = _sortFields.add(withJsonDsl {
        this.put(field, dslObject {
            this["order"] = order.name
            mode?.let {
                this["mode"] = mode.name.lowercase()
            }
            missing?.let {
                this["missing"] = missing
            }
            block?.invoke(this)
        }, PropertyNamingConvention.AsIs)
    })

    fun addScript(
        source: String,
        type: String,
        order: SortOrder = SortOrder.DESC,
        lang: String? = "painless",
        params: Map<String, Any>? = null,
        block: (JsonDsl.() -> Unit)? = null
    ) = _sortFields.add(withJsonDsl {
        this["_script"] = dslObject {
            this["order"] = order.name
            this["type"] = type
            this["script"] = dslObject {
                this["source"] = source
                lang?.let {
                    this["lang"] = lang
                }
                if (params?.isNotEmpty() == true) {
                    this["params"] = withJsonDsl { params.forEach { (key, value) -> this[key] = value } }
                }
            }
            block?.invoke(this)
        }
    })
}

fun SearchDSL.sort(block: SortBuilder.() -> Unit) {
    val builder = SortBuilder()
    block.invoke(builder)
    this["sort"] = builder.sortFields
}

class Collapse : JsonDsl() {
    var field: String by property()
    var innerHits: InnerHits by property()

    class InnerHits : JsonDsl() {
        var name: String by property()
        var resultSize: Int by property("size")
        var collapse: Collapse? by property()
        val maxConcurrentGroupSearches: Int by property()
    }
}

fun Collapse.innerHits(name: String, block: (Collapse.InnerHits.() -> Unit)? = null) {
    val ih = Collapse.InnerHits()
    ih.name = name
    block?.let { ih.apply(it) }
    innerHits = ih
}

fun Collapse.InnerHits.sort(block: SortBuilder.() -> Unit) {
    val builder = SortBuilder()
    block.invoke(builder)
    this["sort"] = builder.sortFields
}

fun SearchDSL.collapse(field: String, block: (Collapse.() -> Unit)? = null) {
    this["collapse"] = Collapse().let { c ->
        c.field = field
        block?.let { c.apply(block) } ?: c
    }
}

fun SearchDSL.collapse(field: KProperty<*>, block: (Collapse.() -> Unit)? = null) = collapse(field.name, block)
fun Collapse.InnerHits.collapse(field: String, block: (Collapse.() -> Unit)? = null) {
    collapse = Collapse().let { c ->
        c.field = field
        block?.let { c.apply(block) } ?: c
    }
}

fun Collapse.InnerHits.collapse(field: KProperty<*>, block: (Collapse.() -> Unit)? = null) = collapse(field.name, block)

fun QueryClauses.matchAll() = ESQuery("match_all")

class Script : JsonDsl() {
    var source by property<String>()
    var params by property<Map<String, Any?>>()
    var lang by property<String>()

    companion object {
        fun create(block: (Script.() -> Unit)) = Script().apply(block)
    }
}

/**
 * Constructs dotted path out of KProperty, enums, and string literals.
 */
fun SearchDSL.dotted(vararg elements: Any) = elements.joinToString(".") { pathComponent ->
    when (pathComponent) {
        is Enum<*> -> pathComponent.name
        is KProperty<*> -> pathComponent.name
        else -> pathComponent.toString()
    }
}
