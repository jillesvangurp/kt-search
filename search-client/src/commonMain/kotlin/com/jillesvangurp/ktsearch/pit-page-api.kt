@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.SearchEngineFamily
import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.searchdsls.querydsl.SortOrder
import com.jillesvangurp.searchdsls.querydsl.sort
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A PIT response that cannot safely be used as a complete, consistently ordered page.
 * [pitId] is the latest ID returned by the server, if present; callers should close
 * it instead of their previous ID when abandoning the snapshot.
 */
class IncompletePitPageException(message: String, val pitId: String?) : IllegalStateException(message)

/**
 * Reads one complete page from a caller-owned PIT. The caller keeps the latest
 * response PIT ID and the last hit's sort values, and closes the PIT when done.
 *
 * [uniqueKeyField] must be a single-valued keyword with doc values and a unique
 * value in each index. OpenSearch sorts by index and that field; Elasticsearch
 * uses its PIT-specific shard/document sort. Payload IDs need not equal storage
 * IDs: returned hit IDs are unchanged. Do not use this helper for legacy data
 * without a suitable unique field; use scroll instead.
 *
 * The query block supplies filters and page size, without PIT, sort, search_after
 * or a nonzero offset. Sort values retain their original JSON types.
 */
suspend fun SearchClient.searchPointInTimePage(
    pitId: String,
    keepAlive: Duration,
    uniqueKeyField: String,
    searchAfter: JsonArray? = null,
    retries: Int = 3,
    retryDelay: Duration = 2.seconds,
    block: SearchDSL.() -> Unit,
): SearchResponse {
    require(pitId.isNotBlank()) { "A PIT ID is required" }
    require(keepAlive.isPositive() && keepAlive.isFinite()) { "A finite positive PIT lifetime is required" }
    require(uniqueKeyField.isNotBlank() && !uniqueKeyField.startsWith("_")) {
        "A unique sortable document field is required" }
    validateEngine(
        "Independent PIT pages require Elasticsearch 7+ or OpenSearch 2+",
        SearchEngineVariant.ES7, SearchEngineVariant.ES8, SearchEngineVariant.ES9,
        SearchEngineVariant.OS2, SearchEngineVariant.OS3,
    )
    val openSearch = engineInfo().variantInfo.variant.family == SearchEngineFamily.Opensearch
    val sortSize = if (openSearch) 2 else 1
    require(searchAfter == null || searchAfter.size == sortSize) { "Unexpected PIT sort values" }
    val query = SearchDSL().apply(block)
    require(listOf("pit", "sort", "search_after").none { query.containsKey(it) }) {
        "PIT paging manages its own snapshot, sort and search_after" }
    require(!query.containsKey("from") || query.from == 0) { "PIT pages cannot use an offset" }
    val afterValues = searchAfter
    query.apply {
        remove("from")
        this["pit"] = withJsonDsl {
            this["id"] = pitId
            this["keep_alive"] = "${keepAlive.inWholeMilliseconds}ms"
        }
        trackTotalHits = "true"
        sort {
            if (openSearch) {
                add("_index", SortOrder.ASC)
                add(uniqueKeyField, SortOrder.ASC)
            } else add("_shard_doc", SortOrder.ASC)
        }
        afterValues?.let { values ->
            // JsonDsl consumes Kotlin values, not kotlinx JsonPrimitive objects.
            this["search_after"] = values.map { value ->
                require(value is JsonPrimitive) { "PIT sort values must be JSON primitives" }
                when {
                    value is JsonNull -> null
                    value.isString -> value.content
                    value.booleanOrNull != null -> value.boolean
                    value.longOrNull != null -> value.long
                    value.doubleOrNull?.isFinite() == true -> value.double
                    else -> error("Unsupported PIT sort value")
                }
            }
        }
    }
    val response = search(
        target = null, dsl = query, allowPartialSearchResults = false,
        retries = retries, retryDelay = retryDelay,
    )
    val shards = response.shards
    if (response.timedOut != false || shards == null || shards.failed != 0 ||
        shards.successful != shards.total ||
        response.hits?.total?.relation != SearchResponse.Hits.TotalRelation.Eq) {
        throw IncompletePitPageException("PIT search did not return a complete page with an exact total", response.pitId)
    }
    if (response.searchHits.any { it.sort?.size != sortSize }) {
        throw IncompletePitPageException("PIT search did not return stable sort values", response.pitId)
    }
    return response
}
