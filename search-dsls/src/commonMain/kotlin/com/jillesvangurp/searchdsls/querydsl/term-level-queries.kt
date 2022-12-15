@file:Suppress("unused")

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

class FuzzyQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
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

fun SearchDSL.ids(
    vararg values: String,
    block: (IdsQuery.() -> Unit)? = null
) = IdsQuery(*values,block = block)

class PrefixQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
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

fun SearchDSL.prefix(
    field: KProperty<*>,
    value: String,
    block: (PrefixQueryConfig.() -> Unit)? = null
) =
    PrefixQuery(field.name, value, block = block)

fun SearchDSL.prefix(
    field: String,
    value: String,
    block: (PrefixQueryConfig.() -> Unit)? = null
) =
    PrefixQuery(field, value, block = block)

enum class RangeRelation { INTERSECTS, CONTAINS, WITHIN }
class RangeQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
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

fun SearchDSL.range(field: KProperty<*>, block: RangeQueryConfig.() -> Unit) =
    RangeQuery(field.name, block = block)

fun SearchDSL.range(field: String, block: RangeQueryConfig.() -> Unit) =
    RangeQuery(field, block = block)


class RegExpQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
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
    regExpQueryConfig: RegExpQueryConfig = RegExpQueryConfig(),
    block: (RegExpQueryConfig.() -> Unit)? = null
) : ESQuery("regexp") {
    init {
        put(field, regExpQueryConfig, PropertyNamingConvention.AsIs)
        regExpQueryConfig.value = value
        block?.invoke(regExpQueryConfig)
    }
}

fun SearchDSL.regExp(
    field: KProperty<*>,
    value: String,
    block: RegExpQueryConfig.() -> Unit
) =
    RegExpQuery(field.name,value, block = block)

fun SearchDSL.regExp(
    field: String,
    value: String,
    block: RegExpQueryConfig.() -> Unit
) =
    RegExpQuery(field,value, block = block)

// BEGIN term-query
class TermQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var value by property<String>()
    var boost by property<Double>()
}

@SearchDSLMarker
class TermQuery(
    field: String,
    value: String,
    termQueryConfig: TermQueryConfig = TermQueryConfig(),
    block: (TermQueryConfig.() -> Unit)? = null
) : ESQuery("term") {

    init {
        put(field, termQueryConfig, PropertyNamingConvention.AsIs)
        termQueryConfig.value = value
        block?.invoke(termQueryConfig)
    }
}

fun SearchDSL.term(
    field: KProperty<*>,
    value: String,
    block: (TermQueryConfig.() -> Unit)? = null
) =
    TermQuery(field.name,value, block = block)

fun SearchDSL.term(
    field: String,
    value: String,
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

fun SearchDSL.terms(
    field: KProperty<*>,
    vararg values: String,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field.name,*values, block = block)

fun SearchDSL.terms(
    field: String,
    vararg values: String,
    block: (TermsQuery.() -> Unit)? = null
) =
    TermsQuery(field,*values, block = block)

class WildCardQueryConfig : JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
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

fun SearchDSL.wildcard(
    field: KProperty<*>,
    value: String,
    block: (WildCardQueryConfig.() -> Unit)? = null
) =
    WildCardQuery(field.name,value, block = block)

fun SearchDSL.wildcard(
    field: String,
    value: String,
    block: (WildCardQueryConfig.() -> Unit)? = null
) =
    WildCardQuery(field,value, block = block)

