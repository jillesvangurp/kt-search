@file:Suppress("unused", "UnusedReceiverParameter", "MemberVisibilityCanBePrivate")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.withJsonDsl
import kotlin.reflect.KProperty

@SearchDSLMarker
class BoolQuery : ESQuery(name = "bool") {
    fun should(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("should").addAll(q.map { it.wrapWithName() })
    fun must(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("must").addAll(q.map { it.wrapWithName() })
    fun mustNot(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("must_not").addAll(q.map { it.wrapWithName() })
    fun filter(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("filter").addAll(q.map { it.wrapWithName() })

    fun should(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("should").addAll(q.map { it.wrapWithName() })
    fun must(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("must").addAll(q.map { it.wrapWithName() })
    fun mustNot(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("must_not").addAll(q.map { it.wrapWithName() })
    fun filter(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("filter").addAll(q.map { it.wrapWithName() })

    var boost by queryDetails.property<Double>()
}

fun SearchDSL.bool(block: BoolQuery.() -> Unit): BoolQuery {
    val q = BoolQuery()
    block.invoke(q)
    return q
}

fun SearchDSL.or(queries: List<ESQuery>) = bool { should(queries) }
fun SearchDSL.and(queries: List<ESQuery>) = bool { filter(queries) }
fun SearchDSL.not(queries: List<ESQuery>) = bool { mustNot(queries) }

fun SearchDSL.or(vararg queries: ESQuery) = bool { should(*queries) }
fun SearchDSL.and(vararg queries: ESQuery) = bool { filter(*queries) }
fun SearchDSL.not(vararg queries: ESQuery) = bool { mustNot(*queries) }

@SearchDSLMarker
class BoostingQuery : ESQuery(name = "boosting") {
    var positive: ESQuery by queryDetails.esQueryProperty()
    var negative: ESQuery by queryDetails.esQueryProperty()
    var negativeBoost: Double by queryDetails.property()
    var boost: Double by queryDetails.property()

    init {
        // default that you can override
        // won't work without a value though
        negativeBoost = 0.5
    }
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
    fun queries(vararg q: ESQuery) = queryDetails.getOrCreateMutableList("queries").addAll(q.map { it.wrapWithName() })
    fun queries(q: List<ESQuery>) = queryDetails.getOrCreateMutableList("queries").addAll(q.map { it.wrapWithName() })
    var tieBreaker: Double by queryDetails.property()
    var boost: Double by queryDetails.property()
}

fun SearchDSL.disMax(block: DisMaxQuery.() -> Unit): DisMaxQuery {
    val q = DisMaxQuery()
    block.invoke(q)
    return q
}

class RandomScoreConfig : JsonDsl() {
    var seed by property<Long>()
    var field by property<String>()

    fun field(field: KProperty<*>) {
        this.field = field.name
    }
}

class FieldValueFactorConfig : JsonDsl() {
    var field by property<String>()
    var factor by property<Double>()
    var modifier by property<FieldValueFactorModifier>()
    var missing by property<Double>()

    @Suppress("EnumEntryName")
    enum class FieldValueFactorModifier { none, log, log1p, log2p, ln, ln1p, ln2p, square, sqrt, reciprocal }

    fun field(field: KProperty<*>) {
        this.field = field.name
    }
}

class DecayFunctionConfig : JsonDsl() {
    var origin by property<String>()
    var scale by property<String>()
    var offset by property<String>()
    var decay by property<Double>()
}

@SearchDSLMarker
class FunctionScoreFunctionConfig : JsonDsl() {
    var weight by property<Double>()
    @Suppress("UNCHECKED_CAST")
    var filter: ESQuery
        get() {
            val map = this["filter"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["filter"] = value.wrapWithName()
        }
    fun fieldValueFactor(block: FieldValueFactorConfig.() -> Unit) {
        this["field_value_factor"] = FieldValueFactorConfig().apply(block)
    }

    fun randomScore(block: (RandomScoreConfig.() -> Unit)? = null) {
        this["random_score"] = RandomScoreConfig().apply {
            block?.invoke(this)
        }
    }

    fun linear(field: String, block: DecayFunctionConfig.() -> Unit) {
        this["linear"] = withJsonDsl {
            this[field] = DecayFunctionConfig().apply(block)
        }
    }

    fun linear(field: KProperty<*>, block: DecayFunctionConfig.() -> Unit) = linear(field.name, block)

    fun exp(field: String, block: DecayFunctionConfig.() -> Unit) {
        this["exp"] = withJsonDsl {
            this[field] = DecayFunctionConfig().apply(block)
        }
    }

    fun exp(field: KProperty<*>, block: DecayFunctionConfig.() -> Unit) = exp(field.name, block)

    fun gauss(field: String, block: DecayFunctionConfig.() -> Unit) {
        this["gauss"] = withJsonDsl {
            this[field] = DecayFunctionConfig().apply(block)
        }
    }

    fun gauss(field: KProperty<*>, block: DecayFunctionConfig.() -> Unit) = gauss(field.name, block)

    fun scriptScore(block: Script.() -> Unit) {
        this["script_score"] = withJsonDsl {
            this["script"] = Script().apply(block)
        }
    }

}

@SearchDSLMarker
class FunctionScoreQuery(
    block: FunctionScoreQuery.() -> Unit
) : ESQuery(name = "function_score") {
    @Suppress("EnumEntryName")
    enum class ScoreMode { multiply, sum, avg, first, max, min }

    @Suppress("EnumEntryName")
    enum class BoostMode { multiply, replace, sum, avg, max, min }

    @Suppress("UNCHECKED_CAST")
    var query: ESQuery
        get() {
            val map = this["query"] as Map<String, JsonDsl>
            val (name, queryDetails) = map.entries.first()
            return ESQuery(name, queryDetails)
        }
        set(value) {
            this["query"] = value.wrapWithName()
        }
    var boost by property<Double>()
    var maxBoost by property<Double>()
    var scoreMode by property<ScoreMode>()
    var boostMode by property<BoostMode>()

    @Suppress("UNCHECKED_CAST")
    val functions: MutableList<FunctionScoreFunctionConfig>
        get() {
            val functions = this["functions"]
            if (functions == null) {
                this["functions"] = mutableListOf<FunctionScoreFunctionConfig>()
            }
            return this["functions"] as MutableList<FunctionScoreFunctionConfig>
        }

    fun function(block: FunctionScoreFunctionConfig.() -> Unit) {
        functions.add(FunctionScoreFunctionConfig().apply(block))
    }

    init {
        this.apply(block)
    }
}

fun SearchDSL.functionScore(block: FunctionScoreQuery.() -> Unit): FunctionScoreQuery {
    return FunctionScoreQuery(block)
}
