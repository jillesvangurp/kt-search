@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

@SearchDSLMarker
class NestedQuery : ESQuery(name = "nested") {
    var path: String by queryDetails.property()
    var query: ESQuery by queryDetails.esQueryProperty()
}

fun SearchDSL.nested(block: NestedQuery.() -> Unit): NestedQuery {
    val q = NestedQuery()
    block.invoke(q)
    return q
}

