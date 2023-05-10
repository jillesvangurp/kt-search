@file:OptIn(FlowPreview::class)
@file:Suppress("EnumEntryName", "unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.VariantRestriction
import com.jillesvangurp.searchdsls.querydsl.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


suspend fun SearchClient.search(
    target: String?,
    allowNoIndices: Boolean? = null,
    allowPartialSearchResults: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    batchedReduceSize: Int? = null,
    ccsMinimizeRoundtrips: Boolean? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    docvalueFields: String? = null,
    expandWildcards: ExpandWildCards? = null,
    explain: Boolean? = null,
    from: Int? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxConcurrentShardRequests: Int? = null,
    preFilterShardSize: Int? = null,
    preference: String? = null,
    q: String? = null,
    requestCache: Boolean? = null,
    restTotalHitsAsInt: Boolean? = null,
    routing: String? = null,
    scroll: String? = null,
    searchType: SearchType? = null,
    seqNoPrimaryTerm: Boolean? = null,
    size: Int? = null,
    sort: String? = null,
    @Suppress("LocalVariableName") _source: String? = null,
    sourceExcludes: String? = null,
    sourceIncludes: String? = null,
    stats: String? = null,
    storedFields: String? = null,
    suggestField: String? = null,
    suggestMode: SuggestMode? = null,
    suggestSize: Int? = null,
    suggestText: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    trackScores: Boolean? = null,
    trackTotalHits: Boolean? = null,
    typedKeys: Boolean? = null,
    version: Boolean? = null,
    extraParameters: Map<String, String>? = null,
    block: (SearchDSL.() -> Unit)? = null
): SearchResponse {
    val dsl = SearchDSL()
    block?.invoke(dsl)
    return search(
        target = target,
        dsl = dsl,
        allowNoIndices = allowNoIndices,
        allowPartialSearchResults = allowPartialSearchResults,
        analyzer = analyzer,
        analyzeWildcard = analyzeWildcard,
        batchedReduceSize = batchedReduceSize,
        ccsMinimizeRoundtrips = ccsMinimizeRoundtrips,
        defaultOperator = defaultOperator,
        df = df,
        docvalueFields = docvalueFields,
        expandWildcards = expandWildcards,
        explain = explain,
        from = from,
        ignoreThrottled = ignoreThrottled,
        ignoreUnavailable = ignoreUnavailable,
        lenient = lenient,
        maxConcurrentShardRequests = maxConcurrentShardRequests,
        preFilterShardSize = preFilterShardSize,
        preference = preference,
        q = q,
        requestCache = requestCache,
        restTotalHitsAsInt = restTotalHitsAsInt,
        routing = routing,
        scroll = scroll,
        searchType = searchType,
        seqNoPrimaryTerm = seqNoPrimaryTerm,
        size = size,
        sort = sort,
        _source = _source,
        sourceExcludes = sourceExcludes,
        sourceIncludes = sourceIncludes,
        stats = stats,
        storedFields = storedFields,
        suggestField = suggestField,
        suggestMode = suggestMode,
        suggestSize = suggestSize,
        suggestText = suggestText,
        terminateAfter = terminateAfter,
        timeout = timeout,
        trackScores = trackScores,
        trackTotalHits = trackTotalHits,
        typedKeys = typedKeys,
        version = version,
        extraParameters = extraParameters
    )
}

suspend fun SearchClient.search(
    target: String?,
    dsl: SearchDSL,
    allowNoIndices: Boolean? = null,
    allowPartialSearchResults: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    batchedReduceSize: Int? = null,
    ccsMinimizeRoundtrips: Boolean? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    docvalueFields: String? = null,
    expandWildcards: ExpandWildCards? = null,
    explain: Boolean? = null,
    from: Int? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxConcurrentShardRequests: Int? = null,
    preFilterShardSize: Int? = null,
    preference: String? = null,
    q: String? = null,
    requestCache: Boolean? = null,
    restTotalHitsAsInt: Boolean? = null,
    routing: String? = null,
    scroll: String? = null,
    searchType: SearchType? = null,
    seqNoPrimaryTerm: Boolean? = null,
    size: Int? = null,
    sort: String? = null,
    _source: String? = null,
    sourceExcludes: String? = null,
    sourceIncludes: String? = null,
    stats: String? = null,
    storedFields: String? = null,
    suggestField: String? = null,
    suggestMode: SuggestMode? = null,
    suggestSize: Int? = null,
    suggestText: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    trackScores: Boolean? = null,
    trackTotalHits: Boolean? = null,
    typedKeys: Boolean? = null,
    version: Boolean? = null,
    extraParameters: Map<String, String>? = null,
) =
    search(
        target = target,
        rawJson = dsl.json(),
        allowNoIndices = allowNoIndices,
        allowPartialSearchResults = allowPartialSearchResults,
        analyzer = analyzer,
        analyzeWildcard = analyzeWildcard,
        batchedReduceSize = batchedReduceSize,
        ccsMinimizeRoundtrips = ccsMinimizeRoundtrips,
        defaultOperator = defaultOperator,
        df = df,
        docvalueFields = docvalueFields,
        expandWildcards = expandWildcards,
        explain = explain,
        from = from,
        ignoreThrottled = ignoreThrottled,
        ignoreUnavailable = ignoreUnavailable,
        lenient = lenient,
        maxConcurrentShardRequests = maxConcurrentShardRequests,
        preFilterShardSize = preFilterShardSize,
        preference = preference,
        q = q,
        requestCache = requestCache,
        restTotalHitsAsInt = restTotalHitsAsInt,
        routing = routing,
        scroll = scroll,
        searchType = searchType,
        seqNoPrimaryTerm = seqNoPrimaryTerm,
        size = size,
        sort = sort,
        _source = _source,
        sourceExcludes = sourceExcludes,
        sourceIncludes = sourceIncludes,
        stats = stats,
        storedFields = storedFields,
        suggestField = suggestField,
        suggestMode = suggestMode,
        suggestSize = suggestSize,
        suggestText = suggestText,
        terminateAfter = terminateAfter,
        timeout = timeout,
        trackScores = trackScores,
        trackTotalHits = trackTotalHits,
        typedKeys = typedKeys,
        version = version,
        extraParameters = extraParameters
    )

enum class SearchOperator { AND, OR }
enum class ExpandWildCards { all, open, closed, hidden, none }
@Suppress("EnumEntryName")
enum class SearchType { query_then_fetch, dfs_query_then_fetch }

enum class SuggestMode { always, missing, popular }

suspend fun SearchClient.search(
    target: String?,
    rawJson: String?,
    allowNoIndices: Boolean? = null,
    allowPartialSearchResults: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    batchedReduceSize: Int? = null,
    ccsMinimizeRoundtrips: Boolean? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    docvalueFields: String? = null,
    expandWildcards: ExpandWildCards? = null,
    explain: Boolean? = null,
    from: Int? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxConcurrentShardRequests: Int? = null,
    preFilterShardSize: Int? = null,
    preference: String? = null,
    q: String? = null,
    requestCache: Boolean? = null,
    restTotalHitsAsInt: Boolean? = null,
    routing: String? = null,
    scroll: String? = null,
    searchType: SearchType? = null,
    seqNoPrimaryTerm: Boolean? = null,
    size: Int? = null,
    sort: String? = null,
    _source: String? = null,
    sourceExcludes: String? = null,
    sourceIncludes: String? = null,
    stats: String? = null,
    storedFields: String? = null,
    suggestField: String? = null,
    suggestMode: SuggestMode? = null,
    suggestSize: Int? = null,
    suggestText: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    trackScores: Boolean? = null,
    trackTotalHits: Boolean? = null,
    typedKeys: Boolean? = null,
    version: Boolean? = null,
    extraParameters: Map<String, String>? = null,
): SearchResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_search").toTypedArray())

        parameter("allow_no_indices", allowNoIndices)
        parameter("allow_partial_search_results", allowPartialSearchResults)
        parameter("analyzer", analyzer)
        parameter("analyze_wildcard", analyzeWildcard)
        parameter("batched_reduce_size", batchedReduceSize)
        parameter("ccs_minimize_roundtrips", ccsMinimizeRoundtrips)
        parameter("default_operator", defaultOperator)
        parameter("df", df)
        parameter("docvalue_fields", docvalueFields)
        parameter("expand_wildcards", expandWildcards)
        parameter("explain", explain)
        parameter("from", from)
        parameter("ignore_throttled", ignoreThrottled)
        parameter("ignore_unavailable", ignoreUnavailable)
        parameter("lenient", lenient)
        parameter("max_concurrent_shard_requests", maxConcurrentShardRequests)
        parameter("pre_filter_shard_size", preFilterShardSize)
        parameter("preference", preference)
        parameter("q", q)
        parameter("request_cache", requestCache)
        parameter("rest_total_hits_as_int", restTotalHitsAsInt)
        parameter("routing", routing)
        parameter("scroll", scroll)
        parameter("search_type", searchType)
        parameter("seq_no_primary_term", seqNoPrimaryTerm)
        parameter("size", size)
        parameter("sort", sort)
        parameter("_source", _source)
        parameter("_source_excludes", sourceExcludes)
        parameter("_source_includes", sourceIncludes)
        parameter("stats", stats)
        parameter("stored_fields", storedFields)
        parameter("suggest_field", suggestField)
        parameter("suggest_mode", suggestMode)
        parameter("suggest_size", suggestSize)
        parameter("suggest_text", suggestText)
        parameter("terminate_after", terminateAfter)
        parameter("timeout", timeout)
        parameter("track_scores", trackScores)
        parameter("track_total_hits", trackTotalHits)
        parameter("typed_keys", typedKeys)
        parameter("version", version)

        parameters(extraParameters)
        if (!rawJson.isNullOrBlank()) {
            rawBody(rawJson)
        }
    }.parse(SearchResponse.serializer(), json)
}

suspend fun SearchClient.scroll(scrollId: String, scroll: Duration = 60.seconds): SearchResponse {
    return restClient.post {
        path("_search", "scroll")
        rawBody(
            """
            {
                "scroll_id": "$scrollId",
                "scroll": "${scroll.inWholeSeconds}s"
            }
        """.trimIndent()
        )
    }.parse(SearchResponse.serializer(), json)
}

/**
 * Delete a scroll by id.
 *
 * Note. this is called from the scroll function so
 * there is no need to call this manually if you use that.
 */
suspend fun SearchClient.deleteScroll(scrollId: String?) {
    if (scrollId != null) {
        restClient.delete {
            path("_scroll", scrollId)
        }
    }
}

/**
 * Scroll through search results for a scrolling search.
 *
 * To start a scrolling search, simply set the scroll parameter to a suitable duration on a normal search (keep alive for the scroll).
 * The response that comes back will have a scroll_id. Then simply pass the response object
 * to the scroll function, and it will scroll the results.
 *
 * @return a flow of hits for the scrolling search.
 */
suspend fun SearchClient.scroll(response: SearchResponse): Flow<SearchResponse.Hit> {
    return flow {
        var resp: SearchResponse = response
        var scrollId: String? = resp.scrollId
        emit(resp)
        while (resp.searchHits.isNotEmpty() && scrollId != null) {
            resp = scroll(scrollId)
            emit(resp)
            if (resp.scrollId != null) {
                scrollId = resp.scrollId
            }
        }
        deleteScroll(scrollId)
    }.flatMapConcat { it.searchHits.asFlow() }
}

@Serializable
data class CreatePointInTimeResponse(val id: String)

/**
 * Create a point in time for use with e.g. search_after.
 *
 * Note, if you use the searchAfter function, it will manage the point in time for you.
 *
 * @return point in time id
 */
suspend fun SearchClient.createPointInTime(name: String, keepAlive: Duration): String {
    return restClient.post {
        path(name, "_pit")
        parameter("keep_alive", "${keepAlive.inWholeSeconds}s")
    }.parse(CreatePointInTimeResponse.serializer(), json).id
}

suspend fun SearchClient.deletePointInTime(id: String): JsonObject {
    return restClient.delete {
        path("_pit")
        body = withJsonDsl {
            this["id"] = id
        }.json()
    }.parse(JsonObject.serializer())
}



/**
 * Perform a deep paging search using point in time and search after.
 *
 * Creates a point in time and then uses it to deep page through search results using
 * search after. Note, this modifies the query via the search dsl.
 *
 * Note, if you specify a sort, be sure to include _shard_doc as a tie breaker.
 *
 * @return a pair of the first response and a flow of hits that when consumed pages through
 * the results using the point in time id and the sort.
 */
@OptIn(FlowPreview::class)
@VariantRestriction(SearchEngineVariant.ES7, SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(
    target: String,
    keepAlive: Duration,
    query: SearchDSL,
    optInToCustomSort: Boolean = false,
): Pair<SearchResponse, Flow<SearchResponse.Hit>> {
    validateEngine(
        "search_after and pit api work slightly different on Opensearch 2.x and not at all on OS1",
        SearchEngineVariant.ES7,
        SearchEngineVariant.ES8
    )
    var pitId = createPointInTime(target, keepAlive)
    query["pit"] = withJsonDsl {
        this["id"] = pitId
    }
    if (!query.containsKey("sort")) {
        query.apply {
            sort {
                // field is added implicitly, the sort isn't.
                // so add it explicitly
                add("_shard_doc", SortOrder.ASC)
            }
        }
    } else {
        if(!optInToCustomSort) {
            error("""Adding a custom sort with search_after can break in a few ways and is probably 
                |not what you want. If you know what you are doing, you can disable this error by setting 
                |optInToCustomSort to true.""".trimMargin())
        }
    }

    val response = search(null, query.json())

    val hitFlow = flow {
        var resp: SearchResponse = response
        emit(resp)
        while (resp.searchHits.isNotEmpty()) {
            resp.hits?.hits?.last()?.sort?.let { sort ->
                query["search_after"] = sort
            }
            resp.pitId?.let { id ->
                pitId = id
                query["pit"] = withJsonDsl {
                    this["id"] = pitId
                    this["keep_alive"] = "${keepAlive.inWholeSeconds}s"
                }
            }
            resp = search(null, query.json())
            emit(resp)
        }
    }.flatMapConcat { it.searchHits.asFlow() }
    return response to hitFlow
}

@VariantRestriction(SearchEngineVariant.ES7, SearchEngineVariant.ES8)
suspend fun SearchClient.searchAfter(
    target: String,
    keepAlive: Duration = 1.minutes,
    optInToCustomSort: Boolean = false,
    block: (SearchDSL.() -> Unit)? = null
): Pair<SearchResponse, Flow<SearchResponse.Hit>> {
    val dsl = SearchDSL().apply(block ?: {})
    return searchAfter(
        target = target,
        keepAlive = keepAlive,
        query = dsl,
        optInToCustomSort = optInToCustomSort
    )
}

@Serializable
data class CountResponse(val count: Long, @SerialName("_shards") val shards: Shards)

/**
 * Variant of the _count api that takes an optional rawBody. Leaving the body empty means
 * doing a match_all search.
 */
suspend fun SearchClient.count(
    target: String? = null,
    rawJson: String? = null,
    allowNoIndices: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    minScore: Double? = null,
    preference: String? = null,
    routing: String? = null,
    terminateAfter: Int? = null
): CountResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_count").toTypedArray())

        parameter("allow_no_indices", allowNoIndices)
        parameter("expand_wildcards", expandWildcards)
        parameter("ignore_throttled", ignoreThrottled)
        parameter("ignore_unavailable", ignoreUnavailable)
        parameter("min_score", minScore)
        parameter("preference", preference)
        parameter("routing", routing)
        parameter("terminate_after", terminateAfter)

        rawJson?.let {
            rawBody(rawJson)
        }
    }.parse(CountResponse.serializer())

}

/**
 * Variant of the _count api that allows you to pass in an ESQuery object. It will be set as
 * the query on the json body that is sent to _count.
 */
suspend fun SearchClient.count(
    target: String? = null,
    query: ESQuery,
    allowNoIndices: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    minScore: Double? = null,
    preference: String? = null,
    routing: String? = null,
    terminateAfter: Int? = null
): CountResponse {
    return count(
        target = target,
        rawJson = query.let {
            withJsonDsl {
                this["query"] = query.wrapWithName()
            }.json()
        },
        allowNoIndices = allowNoIndices,
        expandWildcards = expandWildcards,
        ignoreThrottled = ignoreThrottled,
        ignoreUnavailable = ignoreUnavailable,
        minScore = minScore,
        preference = preference,
        routing = routing,
        terminateAfter = terminateAfter,
    )
}

/**
 * Variant of the _count api that takes a search dsl block so you can set the query. Note, not all parts of the search dsl
 * are supported by _count. E.g. adding sorting would be an error.
 */
suspend fun SearchClient.count(
    target: String? = null,
    allowNoIndices: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    minScore: Double? = null,
    preference: String? = null,
    routing: String? = null,
    terminateAfter: Int? = null,
    block: (SearchDSL.() -> Unit),
): CountResponse {
    return count(
        target = target,
        rawJson = block.let {
            SearchDSL().apply(it).json()
        },
        allowNoIndices = allowNoIndices,
        expandWildcards = expandWildcards,
        ignoreThrottled = ignoreThrottled,
        ignoreUnavailable = ignoreUnavailable,
        minScore = minScore,
        preference = preference,
        routing = routing,
        terminateAfter = terminateAfter,
    )
}

suspend fun SearchClient.msearch(
    target: String?=null,
    body: String?,
    allowNoIndices: Boolean? = null,
    cssMinimizeRoundtrips: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    maxConcurrentSearches: Int? = null,
    maxConcurrentShardRequests: Int? = null,
    preFilterShardSize: Int? = null,
    routing: String? = null,
    searchType: SearchType? = null,
    typedKeys: Boolean? = null,

    ): MultiSearchResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_msearch").toTypedArray())

        parameter("allow_no_indices", allowNoIndices)
        parameter("ccs_minimize_roundtrips", cssMinimizeRoundtrips)
        parameter("expand_wildcards", expandWildcards)
        parameter("ignore_throttled", ignoreThrottled)
        parameter("ignore_unavailable", ignoreUnavailable)
        parameter("max_concurrent_searches", maxConcurrentSearches)
        parameter("max_concurrent_shard_requests", maxConcurrentShardRequests)
        parameter("max_concurrent_shard_requests", maxConcurrentShardRequests)
        parameter("pre_filter_shard_size", preFilterShardSize)
        parameter("routing", routing)
        parameter("search_type", searchType)
        parameter("typed_keys", typedKeys)

        body?.let {
            rawBody(body)
        }
    }.parse(MultiSearchResponse.serializer(), json = json)
}

class MsearchHeader : JsonDsl() {
    var allowNoIndices by property<Boolean>()
    var expandWildcards by property<ExpandWildCards>()
    var ignoreUnavailable by property<Boolean>()
    var index by property<String>()
    var preference by property<String>()
    var requestCache by property<Boolean>()
    var routing by property<String>()
    var searchType by property<SearchType>()
}

fun msearchHeader(block: MsearchHeader.() -> Unit): MsearchHeader {
    return MsearchHeader().apply(block)
}

class MsearchRequest() {
    private val headersAndRequests: MutableList<Pair<MsearchHeader, SearchDSL>> = mutableListOf()

    fun add(header: MsearchHeader = MsearchHeader(), block: SearchDSL.() -> Unit) {
        headersAndRequests.add(header to SearchDSL().apply(block))
    }
    fun toMsearchBody() = headersAndRequests.joinToString("\n") {(h,q) ->
        "${h.json()}\n${q.json()}"
    } + "\n" // trailing new line is required ...
}

suspend fun SearchClient.msearch(
    target: String?,
    allowNoIndices: Boolean? = null,
    cssMinimizeRoundtrips: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreThrottled: Boolean? = null,
    ignoreUnavailable: Boolean? = null,
    maxConcurrentSearches: Int? = null,
    maxConcurrentShardRequests: Int? = null,
    preFilterShardSize: Int? = null,
    routing: String? = null,
    searchType: SearchType? = null,
    typedKeys: Boolean? = null,
    block: MsearchRequest.() -> Unit,

    ): MultiSearchResponse {

    return msearch(
        target = target,
        body = MsearchRequest().apply(block).toMsearchBody(),
        allowNoIndices = allowNoIndices,
        cssMinimizeRoundtrips = cssMinimizeRoundtrips,
        expandWildcards = expandWildcards,
        ignoreThrottled = ignoreThrottled,
        ignoreUnavailable = ignoreUnavailable,
        maxConcurrentSearches = maxConcurrentSearches,
        maxConcurrentShardRequests = maxConcurrentShardRequests,
        preFilterShardSize = preFilterShardSize,
        routing = routing,
        searchType = searchType,
        typedKeys = typedKeys
    )
}

@Serializable
data class MultiSearchResponse(val took: Long, val responses: List<SearchResponse>)