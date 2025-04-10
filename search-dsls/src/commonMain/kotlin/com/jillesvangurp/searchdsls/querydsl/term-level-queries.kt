@file:Suppress("unused", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import kotlin.reflect.KProperty

@SearchDSLMarker
class ExistsQuery(field: String, block: (ExistsQuery.() -> Unit)? = null) : ESQuery("exists") {
    init {
        this.put("field", field, PropertyNamingConvention.AsIs)
        block?.invoke(this)
    }

    val field = queryDetails["field"] as String
    var boost by queryDetails.property<Double>()

}

fun SearchDSL.exists(field: KProperty<*>, block: (ExistsQuery.() -> Unit)? = null) = ExistsQuery(field.name, block)

fun SearchDSL.exists(field: String, block: (ExistsQuery.() -> Unit)? = null) = ExistsQuery(field, block)

class FuzzyQueryConfig : JsonDsl() {
    var boost by property<Double>()
    var value by property<String>()
    var fuzziness by property<String>()
    var maxExpansions by property<Int>()
    var prefixLength by property<Int>()
    var transpositions by property<Boolean>()
    var rewrite by property<String>()
}

@SearchDSLMarker
class FuzzyQuery(
    field: String,
    value: String,
    fuzzyQueryConfig: FuzzyQueryConfig = FuzzyQueryConfig(),
    block: (FuzzyQueryConfig.() -> Unit)? = null
) :
    ESQuery("fuzzy") {
    init {
        put(field, fuzzyQueryConfig, PropertyNamingConvention.AsIs)
        fuzzyQueryConfig.value = value
        block?.invoke(fuzzyQueryConfig)
    }
}

fun SearchDSL.fuzzy(field: KProperty<*>, query: String, block: (FuzzyQueryConfig.() -> Unit)? = null) =
    FuzzyQuery(field.name, query, block = block)

fun SearchDSL.fuzzy(field: String, query: String, block: (FuzzyQueryConfig.() -> Unit)? = null) =
    FuzzyQuery(field, query, block = block)

@SearchDSLMarker
class IdsQuery(vararg values: String, block: (IdsQuery.()->Unit)?=null) : ESQuery("ids") {
    init {
        this["values"] = values
        block?.invoke(this)
    }

    var boost: Double by queryDetails.property()
}

fun QueryClauses.ids(
    vararg values: String,
    block: (IdsQuery.() -> Unit)? = null
) = IdsQuery(*values,block = block)
fun QueryClauses.ids(
    values: Collection<String>,
    block: (IdsQuery.() -> Unit)? = null
) = IdsQuery(*values.toTypedArray(),block = block)

class PrefixQueryConfig : JsonDsl() {
    var boost by property<Double>()
    var value by property<String>()
}

@SearchDSLMarker
class PrefixQuery(
    field: String,
    value: String,
    prefixQueryConfig: PrefixQueryConfig = PrefixQueryConfig(),
    block: (PrefixQueryConfig.() -> Unit)? = null
) : ESQuery("prefix") {
    init {
        put(field, prefixQueryConfig, PropertyNamingConvention.AsIs)
        prefixQueryConfig.value = value
        block?.invoke(prefixQueryConfig)
    }
}

fun QueryClauses.prefix(
    field: KProperty<*>,
    value: String,
    block: (PrefixQueryConfig.() -> Unit)? = null
) =
    PrefixQuery(field.name, value, block = block)

fun QueryClauses.prefix(
    field: String,
    value: String,
    block: (PrefixQueryConfig.() -> Unit)? = null
) =
    PrefixQuery(field, value, block = block)

enum class RangeRelation { INTERSECTS, CONTAINS, WITHIN }
class RangeQueryConfig : JsonDsl() {
    var boost by property<Double>()
    var gt by property<Any>()
    var gte by property<Any>()
    var lt by property<Any>()
    var lte by property<Any>()
    var format by property<String>()
    var relation by property<RangeRelation>()
    var timeZone by property<String>()
}

@SearchDSLMarker
class RangeQuery(
    field: String,
    rangeQueryConfig: RangeQueryConfig = RangeQueryConfig(),
    block: RangeQueryConfig.() -> Unit
) : ESQuery("range") {
    init {
        put(field, rangeQueryConfig, PropertyNamingConvention.AsIs)
        block.invoke(rangeQueryConfig)
    }
}

fun QueryClauses.range(field: KProperty<*>, block: RangeQueryConfig.() -> Unit) =
    RangeQuery(field.name, block = block)

fun QueryClauses.range(field: String, block: RangeQueryConfig.() -> Unit) =
    RangeQuery(field, block = block)


class RegExpQueryConfig : JsonDsl() {
    var boost by property<Double>()
    var value by property<String>()
    var flags by property<String>()
    var maxDeterminizedStates by property<Int>()
    var rewrite by property<String>()
}

@SearchDSLMarker
class RegExpQuery(
    field: String,
    value: String,
    regExpQueryConfig: RegExpQueryConfig? = null,
    block: (RegExpQueryConfig.() -> Unit)? = null
) : ESQuery("regexp") {
    init {
        val reConfig = regExpQueryConfig ?: RegExpQueryConfig()
        put(field, reConfig, PropertyNamingConvention.AsIs)
        reConfig.value = value
        block?.invoke(reConfig)
    }
}

fun QueryClauses.regExp(
    field: KProperty<*>,
    value: String,
    block: (RegExpQueryConfig.() -> Unit)?=null
) =
    RegExpQuery(field.name,value, block = block)

fun QueryClauses.regExp(
    field: String,
    value: String,
    block: (RegExpQueryConfig.() -> Unit)?=null
) =
    RegExpQuery(field,value, block = block)

// BEGIN term-query
class TermQueryConfig : JsonDsl() {
    var value by property<Any>()
    var boost by property<Double>()
    var caseInsensitive by property<Boolean>()
}

@SearchDSLMarker
class TermQuery(
    field: String,
    value: Any,
    termQueryConfig: TermQueryConfig = TermQueryConfig(),
    block: (TermQueryConfig.() -> Unit)? = null
) : ESQuery("term") {

    init {
        put(field, termQueryConfig, PropertyNamingConvention.AsIs)
        termQueryConfig.value = value
        block?.invoke(termQueryConfig)
    }
}

fun QueryClauses.term(
    field: KProperty<*>,
    value: String,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field.name,value, block = block)

fun QueryClauses.term(
    field: String,
    value: String,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field,value, block = block)

fun QueryClauses.term(
    field: KProperty<*>,
    value: Number,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field.name,value, block = block)

fun QueryClauses.term(
    field: String,
    value: Number,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field,value, block = block)

fun QueryClauses.term(
    field: KProperty<*>,
    value: Boolean,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field.name,value, block = block)

fun QueryClauses.term(
    field: String,
    value: Boolean,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field,value, block = block)

// END term-query

@SearchDSLMarker
class TermsQuery(
    field: String,
    vararg values: String,
    block: (TermsQuery.() -> Unit)? = null
) : ESQuery("terms") {
    var boost by queryDetails.property<Double>()
    var index by queryDetails.property<String>()
    var id by queryDetails.property<String>()
    var path by queryDetails.property<String>()
    var routing by queryDetails.property<String>()

    init {
        put(field, values, PropertyNamingConvention.AsIs)
        block?.invoke(this)
    }
}

fun QueryClauses.terms(
    field: KProperty<*>,
    vararg values: String,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field.name,*values, block = block)

fun QueryClauses.terms(
    field: String,
    vararg values: String,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field,*values, block = block)

fun QueryClauses.terms(
    field: KProperty<*>,
    values: Collection<String>,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field.name,*values.toTypedArray(), block = block)

fun QueryClauses.terms(
    field: String,
    values: Collection<String>,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field,*values.toTypedArray(), block = block)

class WildCardQueryConfig : JsonDsl() {
    var value by property<String>()
    var boost by property<Double>()
    var rewrite by property<String>()
}

@SearchDSLMarker
class WildCardQuery(
    field: String,
    value: String,
    wildCardQueryConfig: WildCardQueryConfig = WildCardQueryConfig(),
    block: (WildCardQueryConfig.() -> Unit)? = null
) : ESQuery("wildcard") {

    var boost by queryDetails.property<Double>()
    var caseInsensitive by queryDetails.property<Boolean>()

    init {
        this.put(field, wildCardQueryConfig, PropertyNamingConvention.AsIs)
        wildCardQueryConfig.value = value
        block?.invoke(wildCardQueryConfig)
    }
}

fun QueryClauses.wildcard(
    field: KProperty<*>,
    value: String,
    block: (WildCardQueryConfig.() -> Unit)? = null
) =
    WildCardQuery(field.name,value, block = block)

fun QueryClauses.wildcard(
    field: String,
    value: String,
    block: (WildCardQueryConfig.() -> Unit)? = null
) =
    WildCardQuery(field,value, block = block)


class TermsSetQueryConfig: JsonDsl() {
    var minimumShouldMatchField by property<String>()
    var minimumShouldMatchScript by property<Script>()

    var boost by property<Double>()
}
class TermsSetQuery(field: String, vararg terms: String, block: (TermsSetQueryConfig.() -> Unit)?=null): ESQuery("terms_set") {
    init {
        this[field] = TermsSetQueryConfig().apply {
            this["terms"] = terms
            block?.invoke(this)
        }
    }
}

fun QueryClauses.termsSet(field: String, vararg terms: String,block: (TermsSetQueryConfig.() -> Unit)?=null) = TermsSetQuery(field,terms=terms,block)
fun QueryClauses.termsSet(field: KProperty<*>, vararg terms: String,block: (TermsSetQueryConfig.() -> Unit)?=null) = TermsSetQuery(field.name,terms=terms,block)
fun QueryClauses.termsSet(field: String,  terms: Collection<String>,block: (TermsSetQueryConfig.() -> Unit)?=null) = TermsSetQuery(field,terms=terms.toTypedArray(),block)
fun QueryClauses.termsSet(field: KProperty<*>, terms: Collection<String>,block: (TermsSetQueryConfig.() -> Unit)?=null) = TermsSetQuery(field.name,terms=terms.toTypedArray(),block)

