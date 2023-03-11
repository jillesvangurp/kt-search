package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

suspend fun SearchClient.deleteByQuery(
    target: String?,
    allowNoIndices: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    conflicts: String? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxDocs: Int? = null,
    preference: String? = null,
    q: String? = null,
    refresh: Boolean?=null,
    requestCache: Boolean? = null,
    requestsPerSecond: Int?=null,
    routing: String? = null,
    scroll: String? = null,
    scrollSize: Int? = null,
    searchType: SearchType? = null,
    searchTimeout: String?=null,
    slices: Int?=null,
    sort: String? = null,
    stats: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    version: Boolean? = null,
    waitForExtraShards: String?=null,
    extraParameters: Map<String, String>? = null,
    block: (SearchDSL.() -> Unit)
): DeleteByQueryResponse {
    val dsl = SearchDSL().apply(block)
    return deleteByQuery(
        target = target,
        rawJson = dsl.json(),
        allowNoIndices = allowNoIndices,
        analyzer = analyzer,
        analyzeWildcard = analyzeWildcard,
        conflicts = conflicts,
        defaultOperator = defaultOperator,
        df = df,
        expandWildcards = expandWildcards,
        ignoreUnavailable = ignoreUnavailable,
        lenient = lenient,
        maxDocs = maxDocs,
        preference = preference,
        q = q,
        refresh = refresh,
        requestCache = requestCache,
        requestsPerSecond = requestsPerSecond,
        routing = routing,
        scroll = scroll,
        scrollSize = scrollSize,
        searchType = searchType,
        searchTimeout = searchTimeout,
        slices = slices,
        sort = sort,
        stats = stats,
        terminateAfter = terminateAfter,
        timeout = timeout,
        version = version,
        waitForExtraShards = waitForExtraShards,
        extraParameters = extraParameters
    )
}

suspend fun SearchClient.deleteByQuery(
    target: String?,
    dsl: SearchDSL,
    allowNoIndices: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    conflicts: String? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxDocs: Int? = null,
    preference: String? = null,
    q: String? = null,
    refresh: Boolean?=null,
    requestCache: Boolean? = null,
    requestsPerSecond: Int?=null,
    routing: String? = null,
    scroll: String? = null,
    scrollSize: Int? = null,
    searchType: SearchType? = null,
    searchTimeout: String?=null,
    slices: Int?=null,
    sort: String? = null,
    stats: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    version: Boolean? = null,
    waitForExtraShards: String?=null,
    extraParameters: Map<String, String>? = null,
): DeleteByQueryResponse = deleteByQuery(
    target = target,
    rawJson = dsl.json(),
    allowNoIndices = allowNoIndices,
    analyzer = analyzer,
    analyzeWildcard = analyzeWildcard,
    conflicts = conflicts,
    defaultOperator = defaultOperator,
    df = df,
    expandWildcards = expandWildcards,
    ignoreUnavailable = ignoreUnavailable,
    lenient = lenient,
    maxDocs = maxDocs,
    preference = preference,
    q = q,
    refresh = refresh,
    requestCache = requestCache,
    requestsPerSecond = requestsPerSecond,
    routing = routing,
    scroll = scroll,
    scrollSize = scrollSize,
    searchType = searchType,
    searchTimeout = searchTimeout,
    slices = slices,
    sort = sort,
    stats = stats,
    terminateAfter = terminateAfter,
    timeout = timeout,
    version = version,
    waitForExtraShards = waitForExtraShards,
    extraParameters = extraParameters
)

suspend fun SearchClient.deleteByQuery(
    target: String?,
    rawJson: String?,
    allowNoIndices: Boolean? = null,
    analyzer: String? = null,
    analyzeWildcard: Boolean? = null,
    conflicts: String? = null,
    defaultOperator: SearchOperator? = null,
    df: String? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreUnavailable: Boolean? = null,
    lenient: Boolean? = null,
    maxDocs: Int? = null,
    preference: String? = null,
    q: String? = null,
    refresh: Boolean?=null,
    requestCache: Boolean? = null,
    requestsPerSecond: Int?=null,
    routing: String? = null,
    scroll: String? = null,
    scrollSize: Int? = null,
    searchType: SearchType? = null,
    searchTimeout: String?=null,
    slices: Int?=null,
    sort: String? = null,
    stats: String? = null,
    terminateAfter: Int? = null,
    timeout: Duration? = null,
    version: Boolean? = null,
    waitForExtraShards: String?=null,
    extraParameters: Map<String, String>? = null,
): DeleteByQueryResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_delete_by_query").toTypedArray())

        parameter("allow_no_indices", allowNoIndices)
        parameter("analyzer", analyzer)
        parameter("analyze_wildcard", analyzeWildcard)
        parameter("conflicts", conflicts)
        parameter("default_operator", defaultOperator)
        parameter("df", df)
        parameter("expand_wildcards", expandWildcards)
        parameter("ignore_unavailable", ignoreUnavailable)
        parameter("lenient", lenient)
        parameter("max_docs", maxDocs)
        parameter("preference", preference)
        parameter("q", q)
        parameter("refresh",refresh)
        parameter("request_cache", requestCache)
        parameter("requests_per_second",requestsPerSecond)
        parameter("routing", routing)
        parameter("scroll", scroll)
        parameter("scroll_size",scrollSize)
        parameter("search_type", searchType)
        parameter("search_time_out",searchTimeout)
        parameter("slices",slices)
        parameter("sort", sort)
        parameter("stats", stats)
        parameter("terminate_after", terminateAfter)
        parameter("timeout", timeout)
        parameter("version", version)
        parameter("wait_for_extra_shards",waitForExtraShards)

        parameters(extraParameters)
        if (!rawJson.isNullOrBlank()) {
            rawBody(rawJson)
        }
    }.parse(DeleteByQueryResponse.serializer(), json)
}

@Serializable
data class DeleteByQueryResponse(
    val took: Long,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val total: Long,
    val deleted: Long,
    val batches: Long,
    @SerialName("version_conflicts")
    val versionConflicts: Long,
    val noops: Long,
    val retries: Retries,
    @SerialName("requests_per_second")
    val requestsPerSecond: Double,
    @SerialName("throttled_until_millis")
    val throttledUntilMillis: Long,
    val failures: List<JsonObject>? = null
) {
    @Serializable
    data class Retries(val bulk: Long, val search: Long)
}