@file:Suppress("unused", "UNCHECKED_CAST")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import kotlin.reflect.KProperty

// Begin MATCH_QUERY
enum class MatchOperator { AND, OR }

@Suppress("EnumEntryName")
enum class ZeroTermsQuery { all, none }

@SearchDSLMarker
class MatchQueryConfig : JsonDsl() {
    var query by property<String>()
    var boost by property<Double>()
    var analyzer by property<String>()
    var autoGenerateSynonymsPhraseQuery by property<Boolean>()
    var fuzziness by property<String>()
    var maxExpansions by property<Int>()
    var prefixLength by property<Int>()
    var transpositions by property<Boolean>()
    var fuzzyRewrite by property<String>()
    var lenient by property<Boolean>()
    var operator by property<MatchOperator>()
    var minimumShouldMatch by property<String>()
    var zeroTermsQuery by property<ZeroTermsQuery>()
}

@SearchDSLMarker
class MatchQuery(
    field: String,
    query: String,
    matchQueryConfig: MatchQueryConfig = MatchQueryConfig(),
    block: (MatchQueryConfig.() -> Unit)? = null
) : ESQuery(name = "match") {
    // The map is empty until we assign something
    init {
        put(field, matchQueryConfig, PropertyNamingConvention.AsIs)
        matchQueryConfig.query = query
        block?.invoke(matchQueryConfig)
    }
}

fun SearchDSL.match(
    field: KProperty<*>,
    query: String, block: (MatchQueryConfig.() -> Unit)? = null
) = MatchQuery(field.name, query, block = block)

fun SearchDSL.match(
    field: String,
    query: String, block: (MatchQueryConfig.() -> Unit)? = null
) = MatchQuery(field, query, block = block)
// END MATCH_QUERY

class MatchPhraseQueryConfig : JsonDsl(PropertyNamingConvention.ConvertToSnakeCase) {
    var query by property<String>()
    var boost by property<Double>()
    var analyzer by property<String>()
    var slop by property<Int>()
    var zeroTermsQuery by property<ZeroTermsQuery>()
}

class MatchPhraseQuery(
    field: String,
    query: String,
    matchPhraseQueryConfig: MatchPhraseQueryConfig = MatchPhraseQueryConfig(),
    block: (MatchPhraseQueryConfig.() -> Unit)?
) : ESQuery(name = "match_phrase") {
    init {
        put(field, matchPhraseQueryConfig, PropertyNamingConvention.AsIs)
        matchPhraseQueryConfig.query = query
        block?.invoke(matchPhraseQueryConfig)
    }
}

fun SearchDSL.matchPhrase(
    field: KProperty<*>,
    query: String, block: (MatchPhraseQueryConfig.() -> Unit)? = null
) = MatchPhraseQuery(field.name, query, block = block)

fun SearchDSL.matchPhrase(
    field: String,
    query: String, block: (MatchPhraseQueryConfig.() -> Unit)? = null
) = MatchPhraseQuery(field, query, block = block)

class MatchBoolPrefixQueryConfig : JsonDsl(PropertyNamingConvention.ConvertToSnakeCase) {
    var query by property<String>()
    var boost by property<Double>()
    var analyzer by property<String>()
    var operator by property<MatchOperator>()
    var minimumShouldMatch by property<String>()
    var fuzziness by property<String>()
    var maxExpansions by property<Int>()
    var prefixLength by property<Int>()
    var transpositions by property<Boolean>()
    var fuzzyRewrite by property<String>()
}

class MatchBoolPrefixQuery(
    field: String,
    query: String,
    matchBoolPrefixQueryConfig: MatchBoolPrefixQueryConfig = MatchBoolPrefixQueryConfig(),
    block: (MatchBoolPrefixQueryConfig.() -> Unit)? = null
) : ESQuery(name = "match_bool_prefix") {
    init {
        put(field, matchBoolPrefixQueryConfig, PropertyNamingConvention.AsIs)
        matchBoolPrefixQueryConfig.query = query
        block?.invoke(matchBoolPrefixQueryConfig)
    }
}

fun SearchDSL.matchBoolPrefix(
    field: KProperty<*>,
    query: String, block: (MatchBoolPrefixQueryConfig.() -> Unit)? = null
) = MatchBoolPrefixQuery(field.name, query, block = block)

fun SearchDSL.matchBoolPrefix(
    field: String,
    query: String, block: (MatchBoolPrefixQueryConfig.() -> Unit)? = null
) = MatchBoolPrefixQuery(field, query, block = block)

class MatchPhrasePrefixQueryConfig : JsonDsl(PropertyNamingConvention.ConvertToSnakeCase) {
    var query by property<String>()
    var boost by property<Double>()
    var analyzer by property<String>()
    var maxExpansions by property<Int>()
    var slop by property<Int>()
    var zeroTermsQuery by property<ZeroTermsQuery>()
}

class MatchPhrasePrefixQuery(
    field: String,
    query: String,
    matchPhrasePrefixQueryConfig: MatchPhrasePrefixQueryConfig = MatchPhrasePrefixQueryConfig(),
    block: (MatchPhrasePrefixQueryConfig.() -> Unit)? = null
) : ESQuery(name = "match_phrase_prefix") {
    init {
        put(field, matchPhrasePrefixQueryConfig, PropertyNamingConvention.AsIs)
        matchPhrasePrefixQueryConfig.query = query
        block?.invoke(matchPhrasePrefixQueryConfig)
    }
}

fun SearchDSL.matchPhrasePrefix(
    field: KProperty<*>,
    query: String, block: (MatchPhrasePrefixQueryConfig.() -> Unit)? = null
) = MatchPhrasePrefixQuery(field.name, query, block = block)


fun SearchDSL.matchPhrasePrefix(
    field: String,
    query: String, block: (MatchPhrasePrefixQueryConfig.() -> Unit)? = null
) = MatchPhrasePrefixQuery(field, query, block = block)

@Suppress("EnumEntryName")
enum class MultiMatchType {
    best_fields, most_fields, cross_fields, phrase, phrase_prefix, bool_prefix
}

class MultiMatchQuery(
    query: String,
    vararg fields: String,
    block: (MultiMatchQuery.() -> Unit)? = null
) : ESQuery("multi_match") {
    val query: String get() = this["query"] as String
    val fields: Array<String> get() = this["fields"] as Array<String>

    var type by queryDetails.property<MultiMatchType>()
    var boost by queryDetails.property<Double>()

    // note not all of these are usable with all types; check documentation
    var tieBreaker by queryDetails.property<Double>()
    var analyzer by queryDetails.property<String>()
    var autoGenerateSynonymsPhraseQuery by queryDetails.property<Boolean>()
    var fuzziness by queryDetails.property<String>()
    var maxExpansions by queryDetails.property<Int>()
    var prefixLength by queryDetails.property<Int>()
    var transpositions by queryDetails.property<Boolean>()
    var fuzzyRewrite by queryDetails.property<String>()
    var lenient by queryDetails.property<Boolean>()
    var operator by queryDetails.property<MatchOperator>()
    var minimumShouldMatch by queryDetails.property<String>()
    var zeroTermsQuery by queryDetails.property<ZeroTermsQuery>()
    var slop by queryDetails.property<Int>()

    init {
        this["query"] = query
        this["fields"] = fields
        block?.invoke(this)
    }
}

fun SearchDSL.multiMatch(
    query: String,
    vararg fields: KProperty<*>, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields.map { it.name }.toTypedArray(), block = block)

fun SearchDSL.multiMatch(
    query: String,
    vararg fields: String, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields, block = block)

class QueryStringQuery(
    query: String,
    vararg fields: String,
    block: (QueryStringQuery.() -> Unit)? = null
) : ESQuery("query_string") {

    val query: String get() = this["query"] as String
    val fields: Array<String> get() = this["fields"] as Array<String>
    var boost by queryDetails.property<Double>()

    var defaultField by queryDetails.property<String>()
    var allowLeadingWildcard by queryDetails.property<Boolean>()
    var analyzeWildcard by queryDetails.property<Boolean>()
    var analyzer by queryDetails.property<String>()
    var autoGenerateSynonymsPhraseQuery by queryDetails.property<Boolean>()
    var fuzziness by queryDetails.property<String>()
    var maxExpansions by queryDetails.property<Int>()
    var prefixLength by queryDetails.property<Int>()
    var transpositions by queryDetails.property<Boolean>()
    var fuzzyRewrite by queryDetails.property<String>()
    var lenient by queryDetails.property<Boolean>()
    var defaultOperator by queryDetails.property<MatchOperator>()
    var minimumShouldMatch by queryDetails.property<String>()
    var zeroTermsQuery by queryDetails.property<ZeroTermsQuery>()
    var maxDeterminizedStates by queryDetails.property<Int>()
    var quoteAnalyzer by queryDetails.property<String>()
    var phraseSlop by queryDetails.property<Int>()
    var quoteFieldSuffix by queryDetails.property<String>()
    var rewrite by queryDetails.property<String>()
    var timeZone by queryDetails.property<String>()

    init {
        this["query"] = query
        this["fields"] = fields
        block?.invoke(this)
    }
}

fun SearchDSL.queryString(
    field: KProperty<*>,
    query: String, block: (QueryStringQuery.() -> Unit)? = null
) = QueryStringQuery(field.name, query, block = block)


fun SearchDSL.queryString(
    field: String,
    query: String, block: (QueryStringQuery.() -> Unit)? = null
) = QueryStringQuery(field, query, block = block)

class SimpleQueryStringQuery(
    query: String,
    vararg fields: String,
    block: (SimpleQueryStringQuery.() -> Unit)? = null
) :
    ESQuery("simple_query_string") {
    val query: String get() = this["query"] as String
    val fields: Array<String> get() = this["fields"] as Array<String>
    var boost by queryDetails.property<Double>()

    var defaultField by queryDetails.property<String>()
    var allFields by queryDetails.property<Boolean>()

    var flags by queryDetails.property<String>()
    var analyzeWildcard by queryDetails.property<Boolean>()
    var analyzer by queryDetails.property<String>()
    var autoGenerateSynonymsPhraseQuery by queryDetails.property<Boolean>()
    var fuzziness by queryDetails.property<String>()
    var fuzzyTranspositions by queryDetails.property<Boolean>()
    var fuzzyMaxExpansions by queryDetails.property<Int>()
    var fuzzyPrefixLength by queryDetails.property<Int>()
    var lenient by queryDetails.property<Boolean>()
    var defaultOperator by queryDetails.property<MatchOperator>()
    var minimumShouldMatch by queryDetails.property<String>()
    var quoteFieldSuffix by queryDetails.property<String>()

    init {
        this["query"] = query
        this["fields"] = fields
        block?.invoke(this)
    }
}

fun SearchDSL.simpleQueryString(
    query: String, vararg fields: KProperty<*>, block: (SimpleQueryStringQuery.() -> Unit)? = null
) = SimpleQueryStringQuery(query, *fields.map { it.name }.toTypedArray(), block = block)

fun SearchDSL.simpleQueryString(
    query: String, vararg fields: String, block: (SimpleQueryStringQuery.() -> Unit)? = null
) = SimpleQueryStringQuery(query, *fields, block = block)
