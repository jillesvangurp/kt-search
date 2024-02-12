@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json

enum class ScoreMode {
    avg,min,max,none,sum
}

@SearchDSLMarker
class NestedQuery : ESQuery(name = "nested") {
    var path: String by queryDetails.property()
    var query: ESQuery by queryDetails.esQueryProperty()
    var scoreMode: ScoreMode by queryDetails.property()
    var ignoreUnmapped: Boolean by queryDetails.property()
}

fun QueryClauses.nested(block: NestedQuery.() -> Unit): NestedQuery {
    val q = NestedQuery()
    block.invoke(q)
    return q
}

@SearchDSLMarker
class ParentIdQuery(val type: String, val id: String) : ESQuery("parent_id") {
    init {
        queryDetails["type"] = type
        queryDetails["id"] = id
    }

    var ignoreUnmapped: Boolean by queryDetails.property()
}

fun QueryClauses.parentId(type: String, id: String, block: ParentIdQuery.() -> Unit): ParentIdQuery {
    val q = ParentIdQuery(type = type, id = id)
    block.invoke(q)
    return q
}

fun QueryClauses.parentId(type: String, id: String) = ParentIdQuery(type = type, id = id)

open class ParentChildQuery(queryType: ParentChildQueryType, val type: String) : ESQuery(name = "has_${queryType.name}") {
    init {
        queryDetails[queryType.typeFieldName] = type
    }

    enum class ParentChildQueryType(val typeFieldName: String) {
        parent("parent_type"),
        child("type")
    }

    var query: ESQuery by queryDetails.esQueryProperty()
    var scoreMode: ScoreMode by queryDetails.property()
    var ignoreUnmapped: Boolean by queryDetails.property()
    var minChildren: Int by queryDetails.property()
    var maxChildren: Int by queryDetails.property()

    fun innerHits(block: InnerHits.() -> Unit): InnerHits {
        val q = InnerHits()
        block.invoke(q)
        queryDetails["inner_hits"] = q
        return q
    }

    fun innerHits(): InnerHits {
        val q = InnerHits()
        queryDetails["inner_hits"] = q
        return q
    }

    class InnerHits : JsonDsl() {
        var from: Int by property()
        var innerHitSize: Int by property("size")
        var name: String by property()
        var sort: List<SortField> by property()

        fun sort(block: SortBuilder.() -> Unit) {
            val builder = SortBuilder()
            block.invoke(builder)
            this["sort"] = builder.sortFields
        }
    }
}

@SearchDSLMarker
class HasChildQuery(type: String) : ParentChildQuery(queryType = ParentChildQueryType.child, type = type)

@SearchDSLMarker
class HasParentQuery(type: String) : ParentChildQuery(queryType = ParentChildQueryType.parent, type = type)

fun QueryClauses.hasChild(type: String, block: HasChildQuery.() -> Unit): HasChildQuery {
    val q = HasChildQuery(type)
    block.invoke(q)
    return q
}

fun QueryClauses.hasParent(type: String, block: HasParentQuery.() -> Unit): HasParentQuery {
    val q = HasParentQuery(type)
    block.invoke(q)
    return q
}