@file:OptIn(FlowPreview::class)

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.searchdsls.querydsl.SearchType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class SearchResponse(
    val took: Long,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val hits: Hits?,
    val aggs: JsonObject?,
    @SerialName("_scroll_id")
    val scrollId: String?,
    @SerialName("pit_id")
    val pitId: String?
) {
    @Serializable
    data class Hit(
        @SerialName("_index")
        val index: String,
        @SerialName("_type")
        val type: String,
        @SerialName("_id")
        val id: String,
        @SerialName("_score")
        val score: Double?,
        @SerialName("_source")
        val source: JsonObject?,
        val fields: JsonObject?,
        val sort: JsonArray?
    )

    @Serializable
    data class Hits(
        @SerialName("max_score")
        val maxScore: Double?,
        val total: Total,
        val hits: List<Hit>
    ) {
        @Serializable
        enum class TotalRelation {
            @SerialName("eq")
            Eq,

            @SerialName("gte")
            Gte
        }

        @Serializable
        data class Total(val value: Long, val relation: TotalRelation)
    }
}

val SearchResponse.searchHits get() = this.hits?.hits ?: listOf()

val SearchResponse.total get() = this.hits?.total?.value ?: 0

suspend fun SearchClient.search(
    target: String,
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
    extraParameters: Map<String, String>? = null, block: SearchDSL.() -> Unit
): SearchResponse {
    val dsl = SearchDSL()
    block.invoke(dsl)
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
    target: String,
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
        path(name,"_pit")
        parameter("keep_alive", "${keepAlive.inWholeSeconds}s")
    }.parse(CreatePointInTimeResponse.serializer(),json).id
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
suspend fun SearchClient.searchAfter(target: String, keepAlive: Duration, query: SearchDSL): Pair<SearchResponse,Flow<SearchResponse.Hit>> {
    val pitId = createPointInTime(target, keepAlive)
    query["pit"] = JsonDsl().apply {
        this["id"] = pitId
    }
    if(!query.containsKey("sort")) {
        query["sort"] = withJsonDsl {
            this["_shard_doc"] = "asc"
        }
    }

    val response = search(null,query.json())

    val hitFlow = flow {
        var resp: SearchResponse = response
        emit(resp)
        while (resp.searchHits.isNotEmpty()) {
            resp.hits?.hits?.last()?.sort?.let { sort ->
                query["search_after"] = sort
            }
            resp.pitId?.let { pid ->
                query["pit"] = JsonDsl().apply {
                    this["id"] = pid
                    this["keep_alive"] = "${keepAlive.inWholeSeconds}s"
                }
            }
            resp = search(null,query.json())
            emit(resp)
        }
    }.flatMapConcat { it.searchHits.asFlow() }
    return response to hitFlow
}