@file:Suppress("unused")

package com.jillesvangurp.eskotlinwrapper

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import org.apache.lucene.search.TotalHits
import org.elasticsearch.action.search.ClearScrollRequest
import org.elasticsearch.action.search.MultiSearchResponse
import org.elasticsearch.action.search.SearchResponse
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.clearScrollAsync
import org.elasticsearch.client.scroll
import org.elasticsearch.client.scrollAsync
import org.elasticsearch.core.TimeValue
import org.elasticsearch.search.SearchHit

/**
 * Abstraction for search results that applies to both scrolling and non scrolling searches.
 */
interface SearchResults<T : Any> {
    /**
     * search hits as a sequence or an empty sequence.
     */
    val searchHits: Sequence<SearchHit>

    /**
     * deserialized objects inside the `searchHits`. Deserialization happens lazily.
     */
    val mappedHits: Sequence<T>
        get() {
            return searchHits.map { modelReaderAndWriter.deserialize(it) }
        }

    /**
     * a sequence of pairs of search hits and their deserialized objects.
     */
    val hits: Sequence<Pair<SearchHit, T>>
        get() {
            return searchHits.map {
                Pair(it, modelReaderAndWriter.deserialize(it))
            }
        }

    /**
     * The total hits for the query.
     */
    val totalHits: TotalHits?
    val total: Long
    val totalRelation: TotalHits.Relation

    /**
     * the original search response.
     */
    val searchResponse: SearchResponse

    /**
     * the model reader and writer used for deserialization.
     */
    val modelReaderAndWriter: ModelReaderAndWriter<T>
}

/**
 * Represents a page of search results. Returned for non scrolling searches.
 */
class PagedSearchResults<T : Any>(
    override val searchResponse: SearchResponse,
    override val modelReaderAndWriter: ModelReaderAndWriter<T>
) : SearchResults<T> {

    override val searchHits: Sequence<SearchHit>
        get() {
            return searchResponse.hits?.hits?.asSequence() ?: emptySequence()
        }

    override val totalHits: TotalHits? by lazy { searchResponse.hits?.totalHits }
    override val total by lazy { totalHits?.value ?: 0 }
    override val totalRelation: TotalHits.Relation by lazy {
        totalHits?.relation ?: TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
    }
}

/**
 * Represents scrolling search results. Accessing the [searchHits] causes pages of results to be retrieved lazily.
 *
 * Note. you can only consume the sequence once.
 */
class ScrollingSearchResults<T : Any>(
    override val searchResponse: SearchResponse,
    override val modelReaderAndWriter: ModelReaderAndWriter<T>,
    val restHighLevelClient: RestHighLevelClient,
    val scrollTtlInMinutes: Long,
    val defaultRequestOptions: RequestOptions = RequestOptions.DEFAULT
) : SearchResults<T> {

    override val searchHits: Sequence<SearchHit>
        get() {
            return responses().flatMap { response ->
                response.hits?.asSequence() ?: emptySequence()
            }
        }

    override val totalHits: TotalHits? by lazy { searchResponse.hits?.totalHits }
    override val total by lazy { totalHits?.value ?: 0 }
    override val totalRelation: TotalHits.Relation by lazy {
        totalHits?.relation ?: TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO
    }

    private fun responses(): Sequence<SearchResponse> {
        return generateSequence(
            // FIXME it currently seems impossible to do a suspending version of this
            seed = searchResponse,
            nextFunction = {
                val currentScrollId = it.scrollId
                if (currentScrollId != null && it.hits?.hits?.isNotEmpty() == true) {
                    restHighLevelClient.scroll(currentScrollId, scrollTtlInMinutes, defaultRequestOptions)
                } else {
                    if (currentScrollId != null) {
                        val clearScrollRequest = ClearScrollRequest()
                        clearScrollRequest.addScrollId(currentScrollId)
                        restHighLevelClient.clearScroll(clearScrollRequest, defaultRequestOptions)
                    }
                    null
                }
            }
        )
    }
}

class AsyncSearchResults<T : Any>(
    private val client: RestHighLevelClient,
    private val modelReaderAndWriter: ModelReaderAndWriter<T>,
    private val scrollTtlInMinutes: Long,
    val firstResponse: SearchResponse,
    private val defaultRequestOptions: RequestOptions = RequestOptions.DEFAULT
) {
    val totalHits: TotalHits? by lazy { firstResponse.hits?.totalHits }
    val total by lazy { totalHits?.value ?: 0 }
    val totalRelation: TotalHits.Relation by lazy { totalHits?.relation ?: TotalHits.Relation.GREATER_THAN_OR_EQUAL_TO }

    fun rawResponses() = flow<SearchResponse> {
        emit(firstResponse)
        var next = fetchNext(firstResponse, scrollTtlInMinutes)
        while (next != null) {
            emit(next)
            next = fetchNext(next, scrollTtlInMinutes)
        }
    }

    /**
     * Returns just the `SearchHit`s as a `Flow`. This does not attempt to deserialize anything.
     */
    val searchHits: Flow<SearchHit>
        get() {
            return rawResponses().transform {
                it.hits.forEach { hit ->
                    emit(hit)
                }
            }
        }

    /**
     * A Flow of pairs of `SearchHit`s and the deserialized `T` as a `Flow`. Use this if you need both.
     */
    val hits = searchHits.map { it to modelReaderAndWriter.deserialize(it) }

    /**
     * A `Flow` of `T`. Use this if you don't need the underlying `SearchHit`.
     */
    val mappedHits = searchHits.map { modelReaderAndWriter.deserialize(it) }

    private suspend fun fetchNext(it: SearchResponse, ttl: Long): SearchResponse? {
        val currentScrollId = it.scrollId
        return if (currentScrollId != null && it.hits?.hits?.isNotEmpty() == true) {
            client.scrollAsync(
                SearchScrollRequest(currentScrollId).scroll(
                    TimeValue.timeValueMinutes(
                        ttl
                    )
                ),
                defaultRequestOptions
            )
        } else {
            if (currentScrollId != null) {
                val clearScrollRequest = ClearScrollRequest()
                clearScrollRequest.addScrollId(currentScrollId)
                client.clearScrollAsync(clearScrollRequest, defaultRequestOptions)
            }
            null
        }
    }
}

class MultiSearchResults<T : Any>(
    private val modelReaderAndWriter: ModelReaderAndWriter<T>,
    private val multiSearchResponse: MultiSearchResponse
) {
    val took = multiSearchResponse.took.millis()
    val responses: List<Result<SearchResults<T>>> = multiSearchResponse.responses.map { item ->
        if (item.isFailure) {
            Result.failure(item.failure!!)
        } else {
            Result.success(
                PagedSearchResults(
                    item.response,
                    modelReaderAndWriter
                )
            )
        }
    }
}

class AsyncMultiSearchResults<T : Any>(
    private val client: RestHighLevelClient,
    private val modelReaderAndWriter: ModelReaderAndWriter<T>,
    private val scrollTtlInMinutes: Long,
    private val multiSearchResponse: MultiSearchResponse,
    private val defaultRequestOptions: RequestOptions = RequestOptions.DEFAULT
) {
    val took = multiSearchResponse.took.millis()
    val responses: List<Result<AsyncSearchResults<T>>> = multiSearchResponse.responses.map { item ->
        if (item.isFailure) {
            Result.failure(item.failure!!)
        } else {
            Result.success(
                AsyncSearchResults(
                    client,
                    modelReaderAndWriter,
                    scrollTtlInMinutes,
                    item.response!!,
                    defaultRequestOptions
                )
            )
        }
    }
}
