@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

@SearchDSLMarker
class BoolQuery : ESQuery(name = "bool") {
    fun should(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("should").addAll(q.map { it.toMap() })
    fun must(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("must").addAll(q.map { it.toMap() })
    fun mustNot(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("must_not").addAll(q.map { it.toMap() })
    fun filter(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("filter").addAll(q.map { it.toMap() })

    fun should(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("should").addAll(q.map { it.toMap() })
    fun must(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("must").addAll(q.map { it.toMap() })
    fun mustNot(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("must_not").addAll(q.map { it.toMap() })
    fun filter(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("filter").addAll(q.map { it.toMap() })

    var boost by queryDetails.property<Double>()
}

fun SearchDSL.bool(block: BoolQuery.() -> Unit): BoolQuery {
    val q = BoolQuery()
    block.invoke(q)
    return q
}

@SearchDSLMarker
class BoostingQuery : ESQuery(name = "boosting") {
    var positive: ESQuery by queryDetails.esQueryProperty()
    var negative: ESQuery by queryDetails.esQueryProperty()
    var negativeBoost: Double by queryDetails.property()
    var boost: Double by queryDetails.property()
}

fun SearchDSL.boosting(block: BoostingQuery.() -> Unit): BoostingQuery {
    val q = BoostingQuery()
    block.invoke(q)
    return q
}

@SearchDSLMarker
class ConstantScoreQuery : ESQuery(name = "constant_score") {
    var filter: ESQuery by queryDetails.esQueryProperty()
    var boost: Double by queryDetails.property()
}

fun SearchDSL.constantScore(block: ConstantScoreQuery.() -> Unit): ConstantScoreQuery {
    val q = ConstantScoreQuery()
    block.invoke(q)
    return q
}

@SearchDSLMarker
class DisMaxQuery : ESQuery(name = "dis_max") {
    fun queries(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("queries").addAll(q.map { it.toMap() })
    fun queries(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("queries").addAll(q.map { it.toMap() })
    var tieBreaker: Double by queryDetails.property()
    var boost: Double by queryDetails.property()
}

fun SearchDSL.disMax(block: DisMaxQuery.() -> Unit): DisMaxQuery {
    val q = DisMaxQuery()
    block.invoke(q)
    return q
}
