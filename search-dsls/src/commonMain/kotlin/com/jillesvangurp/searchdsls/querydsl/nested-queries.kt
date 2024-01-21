@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

@SearchDSLMarker
class NestedQuery : ESQuery(name = "nested") {
    enum class ScoreMode {
        avg,min,max,none,sum
    }
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

