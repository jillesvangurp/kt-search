@file:Suppress("unused")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@DslMarker
annotation class SearchDSLMarker

@SearchDSLMarker
open class ESQuery(
    val name: String,
    val queryDetails: JsonDsl = JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase)
) :
    IJsonDsl by queryDetails {

    fun toMap(): Map<String, Any> = dslObject { this[name] = queryDetails }

    override fun toString(): String {
        return toMap().toString()
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
            this@esQueryProperty[property.name] = value.toMap()
        }
    }
}

fun customQuery(name: String, block: JsonDsl.() -> Unit): ESQuery {
    val q = ESQuery(name)
    block.invoke(q.queryDetails)
    return q
}

@Suppress("UNCHECKED_CAST")
class SearchDSL : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var from: Int by property()
    var trackTotalHits: String by property()

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
            this["query"] = value.toMap()
        }

    var postFilter: ESQuery
        get() {
            val map = this["post_filter"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["post_filter"] = value.toMap()
        }
}

enum class SortOrder { ASC, DESC }
enum class SortMode { MIN, MAX, SUM, AVG, MEDIAN }

@Suppress("UNUSED_PARAMETER")
class SortField(field: String, order: SortOrder? = null, mode: SortMode? = null)

class SortBuilder {
    internal val sortFields = mutableListOf<Any>()

    operator fun String.unaryPlus() = sortFields.add(this)
    operator fun KProperty<*>.unaryPlus() = sortFields.add(this)

    fun add(
        field: KProperty<*>,
        order: SortOrder = SortOrder.DESC,
        mode: SortMode? = null,
        missing: String?,
        block: (JsonDsl.() -> Unit)? = null
    ) =
        add(field = field.name, order = order, mode = mode, missing = missing, block = block)

    fun add(
        field: String,
        order: SortOrder = SortOrder.DESC,
        mode: SortMode? = null,
        missing: String? = null,
        block: (JsonDsl.() -> Unit)? = null
    ) = sortFields.add(withJsonDsl {
        this.put(field, dslObject {
            this["order"] = order.name
            mode?.let {
                this["mode"] = mode.name.lowercase()
            }
            missing?.let {
                this["missing"] = it
            }
            block?.invoke(this)
        }, PropertyNamingConvention.AsIs)
    })
}

fun SearchDSL.sort(block: SortBuilder.() -> Unit) {
    val builder = SortBuilder()
    block.invoke(builder)
    this["sort"] = builder.sortFields
}

fun SearchDSL.matchAll() = ESQuery("match_all")

@Suppress("EnumEntryName")
enum class ExpandWildCards { all, open, closed, hidden, none }

@Suppress("EnumEntryName")
enum class SearchType { query_then_fetch, dfs_query_then_fetch }

@Suppress("SpellCheckingInspection")
class MultiSearchHeader : JsonDsl() {
    var allowNoIndices by property<Boolean>()
    var ccsMinimizeRoundtrips by property<Boolean>()
    var expandWildcards by property<ExpandWildCards>()
    var ignoreThrottled by property<Boolean>()
    var ignoreUnavailable by property<Boolean>()
    var maxConcurrentSearches by property<Int>()
    var maxConcurrentShardRequests by property<Int>()
    var preFilterShardSize by property<Int>()
    var restTotalHitsAsInt by property<Int>()
    var routing by property<String>()
    var searchType by property<SearchType>()
    var typedKeys by property<Boolean>()
}

class MultiSearchDSL(internal val jsonDslSerializer: JsonDslSerializer) {
    private val json = mutableListOf<String>()
    fun add(header: MultiSearchHeader, query: SearchDSL) {
        json.add(jsonDslSerializer.serialize(header))
        json.add(jsonDslSerializer.serialize(query))
    }

    fun add(header: MultiSearchHeader, queryBlock: SearchDSL.() -> Unit) {
        val dsl = SearchDSL()
        queryBlock.invoke(dsl)
        json.add(jsonDslSerializer.serialize(header))
        json.add(jsonDslSerializer.serialize(dsl))
    }

    fun header(headerBlock: MultiSearchHeader.() -> Unit): MultiSearchHeader {
        val header = MultiSearchHeader()
        headerBlock.invoke(header)
        return header
    }

    infix fun MultiSearchHeader.withQuery(queryBlock: SearchDSL.() -> Unit) {
        val dsl = SearchDSL()
        queryBlock.invoke(dsl)
        add(this, dsl)
    }

    fun withQuery(queryBlock: SearchDSL.() -> Unit) {
        val dsl = SearchDSL()
        queryBlock.invoke(dsl)
        add(MultiSearchHeader(), dsl)
    }

    fun requestBody(): String {
        return json.joinToString("\n") + "\n"
    }

    override fun toString(): String {
        return requestBody()
    }
}
