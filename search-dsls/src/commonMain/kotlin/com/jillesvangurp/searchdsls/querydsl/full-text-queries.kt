@file:Suppress("unused", "UNCHECKED_CAST", "UnusedReceiverParameter")

package com.jillesvangurp.searchdsls.querydsl

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.withJsonDsl
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

fun QueryClauses.match(
    field: KProperty<*>,
    query: String, block: (MatchQueryConfig.() -> Unit)? = null
) = MatchQuery(field.name, query, block = block)

fun QueryClauses.match(
    field: String,
    query: String, block: (MatchQueryConfig.() -> Unit)? = null
) = MatchQuery(field, query, block = block)
// END MATCH_QUERY

class MatchPhraseQueryConfig : JsonDsl() {
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

fun QueryClauses.matchPhrase(
    field: KProperty<*>,
    query: String, block: (MatchPhraseQueryConfig.() -> Unit)? = null
) = MatchPhraseQuery(field.name, query, block = block)

fun QueryClauses.matchPhrase(
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

fun QueryClauses.matchBoolPrefix(
    field: KProperty<*>,
    query: String, block: (MatchBoolPrefixQueryConfig.() -> Unit)? = null
) = MatchBoolPrefixQuery(field.name, query, block = block)

fun QueryClauses.matchBoolPrefix(
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

fun QueryClauses.matchPhrasePrefix(
    field: KProperty<*>,
    query: String, block: (MatchPhrasePrefixQueryConfig.() -> Unit)? = null
) = MatchPhrasePrefixQuery(field.name, query, block = block)


fun QueryClauses.matchPhrasePrefix(
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

fun QueryClauses.multiMatch(
    query: String,
    vararg fields: KProperty<*>, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields.map { it.name }.toTypedArray(), block = block)

fun QueryClauses.multiMatch(
    query: String,
    vararg fields: String, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields, block = block)
fun QueryClauses.multiMatch(
    query: String,
    fields: Collection<KProperty<*>>, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields.map { it.name }.toTypedArray(), block = block)

fun QueryClauses.multiMatch(
    query: String,
    fields: Collection<String>, block: (MultiMatchQuery.() -> Unit)? = null
) = MultiMatchQuery(query, *fields.toTypedArray(), block = block)

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

fun QueryClauses.queryString(
    query: String,
    vararg fields: KProperty<*>,
    block: (QueryStringQuery.() -> Unit)? = null
) = QueryStringQuery(query,fields=fields.map { it.name }.toTypedArray(), block = block)


fun QueryClauses.queryString(
    query: String,
    vararg fields: String,
    block: (QueryStringQuery.() -> Unit)? = null
) = QueryStringQuery(query, fields=fields, block = block)

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

fun QueryClauses.simpleQueryString(
    query: String, vararg fields: KProperty<*>, block: (SimpleQueryStringQuery.() -> Unit)? = null
) = SimpleQueryStringQuery(query, *fields.map { it.name }.toTypedArray(), block = block)

fun QueryClauses.simpleQueryString(
    query: String, vararg fields: String, block: (SimpleQueryStringQuery.() -> Unit)? = null
) = SimpleQueryStringQuery(query, *fields, block = block)


class CombinedFieldsQuery(query: String, vararg fields: String, block: (CombinedFieldsQuery.() -> Unit)?=null): ESQuery("combined_fields") {
    var operator by property<MatchOperator>()
    var autoGenerateSynonymsPhraseQuery by property<Boolean>()
    var minimumShouldMatch by property<String>()
    var zeroTermsQuery by property<ZeroTermsQuery>()
    init {
        this["query"] = query
        this["fields"] = fields

        block?.invoke(this)
    }
}

fun QueryClauses.combinedFields(query: String, vararg fields: String, block: (CombinedFieldsQuery.() -> Unit)?=null) = CombinedFieldsQuery(
    query = query,
    fields = fields,
    block = block
)
fun QueryClauses.combinedFields(query: String, vararg fields: KProperty<*>, block: (CombinedFieldsQuery.() -> Unit)?=null) = CombinedFieldsQuery(
    query = query,
    fields = fields.map { it.name }.toTypedArray(),
    block = block
)

sealed class IntervalsRule(val name: String) : JsonDsl() {

    class Match : IntervalsRule("match") {
        var query by property<String>()
        var maxGaps by property<Int>()
        var ordered by property<Boolean>()
        var analyzer by property<String>()
        fun withFilter(block: IntervalsFilter.() -> Unit) {
            // can't be called filter because that clashes with Map.filter ...
            this["filter"] = IntervalsFilter().apply(block)
        }
    }
    class Prefix : IntervalsRule("prefix") {
        var prefix by property<String>()
        var analyzer by property<String>()
        var useField by property<String>()
    }

    class Wildcard : IntervalsRule("wildcard") {
        var pattern by property<String>()
        var analyzer by property<String>()
        var useField by property<String>()
    }
    class Fuzzy : IntervalsRule("fuzzy") {
        var term by property<String>()
        var prefixLength by property<Int>()
        var transpositions by property<Boolean>()
        var fuzziness by property<String>()
        var analyzer by property<String>()
        var useField by property<String>()

    }
    class AllOf : IntervalsRule("all_of") {
        fun intervals(vararg rules:IntervalsRule) = getOrCreateMutableList("intervals").addAll(rules.map { withJsonDsl { this[it.name] = it } })
        var maxGaps by property<Int>()
        var ordered by property<Boolean>()
        fun withFilter(block: IntervalsFilter.() -> Unit) {
            // can't be called filter because that clashes with Map.filter ...
            this["filter"] = IntervalsFilter().apply(block)
        }
    }
    class AnyOf : IntervalsRule("any_of") {
        fun intervals(vararg rules:IntervalsRule) = getOrCreateMutableList("intervals").addAll(rules.map { withJsonDsl { this[it.name] = it } })
        fun withFilter(block: IntervalsFilter.() -> Unit) {
            // can't be called filter because that clashes with Map.filter ...
            this["filter"] = IntervalsFilter().apply(block)
        }
    }

}

class IntervalsFilter: JsonDsl() {
    fun after(query: IntervalsRule) {
        this["after"] = withJsonDsl { this[query.name] = query }
    }

    fun before(query: IntervalsRule) {
        this["before"] = withJsonDsl { this[query.name] = query }
    }

    fun containedBy(query: IntervalsRule) {
        this["contained_by"] = withJsonDsl { this[query.name] = query }
    }

    fun containing(query: IntervalsRule) {
        this["containing"] = withJsonDsl { this[query.name] = query }
    }

    fun notContainedBy(query: IntervalsRule) {
        this["not_contained_by"] = withJsonDsl { this[query.name] = query }
    }

    fun notContaining(query: IntervalsRule) {
        this["not_containing"] = withJsonDsl { this[query.name] = query }
    }

    fun notOverlapping(query: IntervalsRule) {
        this["not_overlapping"] = withJsonDsl { this[query.name] = query }
    }

    fun overlapping(query: IntervalsRule) {
        this["overlapping"] = withJsonDsl { this[query.name] = query }
    }

    var script by property<Script>()
}


@Suppress("IdentifierGrammar")
class IntervalsQuery(val field: String) :ESQuery("intervals") {

    fun rule(rule: IntervalsRule) {
        this[field] = withJsonDsl {
            this[rule.name] = rule
        }
    }

    fun matchRule(block: IntervalsRule.Match.() -> Unit) = IntervalsRule.Match().apply(block)
    fun prefixRule(block: IntervalsRule.Prefix.() -> Unit) = IntervalsRule.Prefix().apply(block)
    fun wildcardRule(block: IntervalsRule.Wildcard.() -> Unit) = IntervalsRule.Wildcard().apply(block)
    fun fuzzyRule(block: IntervalsRule.Fuzzy.() -> Unit) = IntervalsRule.Fuzzy().apply(block)
    fun allOfRule(block: IntervalsRule.AllOf.() -> Unit) = IntervalsRule.AllOf().apply(block)
    fun anyOfRule(block: IntervalsRule.AnyOf.() -> Unit) = IntervalsRule.AnyOf().apply(block)
}

fun QueryClauses.intervals(field: String, block: IntervalsQuery.() -> IntervalsRule): IntervalsQuery {
    return IntervalsQuery(field).apply {
        rule(block.invoke(this))
    }
}