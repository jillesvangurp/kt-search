package com.jillesvangurp.eskotlinwrapper

import com.jillesvangurp.searchdsls.querydsl.MultiSearchDSL
import mu.KotlinLogging
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.get.GetResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.index.IndexResponse
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.support.ActiveShardCount
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.support.master.AcknowledgedResponse
import org.elasticsearch.client.*
import org.elasticsearch.client.core.CountRequest
import org.elasticsearch.client.indices.CreateIndexRequest
import org.elasticsearch.client.indices.GetIndexRequest
import org.elasticsearch.client.indices.GetMappingsRequest
import org.elasticsearch.client.indices.PutMappingRequest
import org.elasticsearch.cluster.metadata.AliasMetadata
import org.elasticsearch.core.TimeValue
import org.elasticsearch.xcontent.XContentType
import org.elasticsearch.search.fetch.subphase.FetchSourceContext
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/**
 * Repository abstraction that allows you to work with indices.
 *
 * You should create a Repository for each index you work with. You need to specify a [ModelReaderAndWriter] for serialization and deserialization.
 *
 * @see `RestHighLevelClient.indexRepository` for a convenient way to create a repository.
 *
 * @param T the type of the object that is stored in the index.
 * @param indexName name of the index
 * @param indexReadAlias Alias used for read operations. If you are using aliases, you can separate reads and writes. Defaults to indexName.
 * @param indexWriteAlias Alias used for write operations. If you are using aliases, you can separate reads and writes. Defaults to indexName.
 * @param type the type of the documents in the index; defaults to null. Since ES 6, there can only be one type. Types are deprecated in ES 7 and removed in ES 8.
 * @param modelReaderAndWriter serialization of your model class.
 * @param refreshAllowed if false, the refresh will throw an exception. Defaults to false.
 * @param defaultRequestOptions passed on all API calls. Defaults to `RequestOptions.DEFAULT`. Use this to set custom headers or override on each call on the repository.
 * @param fetchSourceContext if not null, will be passed to Get and Search requests.
 *
 */
class IndexRepository<T : Any>(
    val indexName: String,
    private val client: RestHighLevelClient,
    internal val modelReaderAndWriter: ModelReaderAndWriter<T>,
    private val refreshAllowed: Boolean = false,
    @Deprecated("Types are deprecated in ES 7.x and will be removed in v8") val type: String? = null,
    val indexWriteAlias: String = indexName,
    val indexReadAlias: String = indexWriteAlias,
    private val fetchSourceContext: FetchSourceContext? = null,
    internal val defaultRequestOptions: RequestOptions = RequestOptions.DEFAULT
) {
    /**
     * Create the index.
     *
     * @param block customize the `CreateIndexRequest`
     *
     * ```
     * {
     *   source(settings, XContentType.JSON)
     * }
     * ```
     */
    fun createIndex(
        requestOptions: RequestOptions = this.defaultRequestOptions,
        waitForActiveShards: ActiveShardCount? = null,
        block: CreateIndexRequest.() -> Unit
    ) {

        val indexRequest = CreateIndexRequest(indexName)
        if (waitForActiveShards != null) {
            indexRequest.waitForActiveShards(waitForActiveShards)
        }
        block.invoke(indexRequest)

        client.indices().create(indexRequest, requestOptions)
    }

    fun getSettings() = client.indices().getSettings(GetSettingsRequest().indices(indexName), defaultRequestOptions)
    fun getMappings() = client.indices().getMapping(GetMappingsRequest().indices(indexName), defaultRequestOptions)
    fun getMappingsAndSettings() = client.indices().get(GetIndexRequest(indexName), defaultRequestOptions)

    /**
     * Delete the index associated with the repository. Returns true if successful or false if the index did not exist
     */
    fun deleteIndex(requestOptions: RequestOptions = this.defaultRequestOptions): Boolean {
        return try {
            client.indices().delete(DeleteIndexRequest(indexName), requestOptions)
            true
        } catch (e: ElasticsearchStatusException) {
            if (e.status().status == 404) {
                // 404 means there was nothing to delete
                false
            } else {
                // this would be unexpected
                throw e
            }
        }
    }

    fun updateIndexMapping(
        requestOptions: RequestOptions = this.defaultRequestOptions,
        block: PutMappingRequest.() -> Unit
    ): AcknowledgedResponse {
        val updateMappingRequest = PutMappingRequest(indexName)
        block.invoke(updateMappingRequest)
        return client.indices().putMapping(updateMappingRequest, requestOptions)
    }

    /**
     * Returns a set of the current `AliasMetaData` associated with the `indexName`.
     */
    fun currentAliases(requestOptions: RequestOptions = this.defaultRequestOptions): Set<AliasMetadata> {
        return client.indices().getAlias(GetAliasesRequest().indices(indexName), requestOptions).aliases[this.indexName]
            ?: throw IllegalStateException("Inde $indexName does not exist")
    }

    /**
     * Index a document with a given `id`. Set `create` to `false` for upserts. Otherwise it fails on creating documents that already exist.
     *
     * The id is nullable and elasticsearch will generate an id for you if you set it to null.
     *
     * You can optionally specify `seqNo` and `primaryTerm` to implement optimistic locking. However, you should use
     * [update] which does this for you.
     */
    @Suppress("DEPRECATION")
    fun index(
        id: String? = null,
        obj: T,
        create: Boolean = true,
        seqNo: Long? = null,
        primaryTerm: Long? = null,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.NONE,
        requestOptions: RequestOptions = this.defaultRequestOptions,
        pipeline: String? = null
    ): IndexResponse {
        val indexRequest = IndexRequest()
            .index(indexWriteAlias)
            .source(modelReaderAndWriter.serialize(obj), XContentType.JSON)
            .let { indexRequest ->
                indexRequest.refreshPolicy = refreshPolicy
                indexRequest.pipeline = pipeline

                if (id != null) {
                    indexRequest.id(id).create(create)
                } else {
                    indexRequest
                }
            }

        if (!type.isNullOrBlank()) {
            indexRequest.type(type)
        }
        if (seqNo != null) {
            indexRequest.setIfSeqNo(seqNo)
            indexRequest.setIfPrimaryTerm(
                primaryTerm
                    ?: throw IllegalArgumentException("you must also set primaryTerm when setting a seqNo")
            )
        }
        return client.index(
            indexRequest, requestOptions
        )
    }

    /**
     * Updates document identified by `id` by fetching the current version with [get] and then applying the [transformFunction] to produce the updated version.
     *
     * if [maxUpdateTries] > 0, it will deal with version conflicts (e.g. due to concurrent updates) by retrying with the latest version.
     */
    fun update(
        id: String,
        maxUpdateTries: Int = 2,
        requestOptions: RequestOptions = this.defaultRequestOptions,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.NONE,
        pipeline: String? = null,
        transformFunction: (T) -> T
    ): IndexResponse {
        return update(
            tries = 0,
            id = id,
            transformFunction = transformFunction,
            maxUpdateTries = maxUpdateTries,
            refreshPolicy = refreshPolicy,
            requestOptions = requestOptions,
            pipeline = pipeline
        )
    }

    @Suppress("DEPRECATION")
    private fun update(
        tries: Int,
        id: String,
        transformFunction: (T) -> T,
        maxUpdateTries: Int,
        refreshPolicy: WriteRequest.RefreshPolicy,
        requestOptions: RequestOptions,
        pipeline: String? = null,
        ): IndexResponse {
        try {
            val getRequest = GetRequest().index(indexWriteAlias).id(id)
            if (!type.isNullOrBlank()) {
                getRequest.type(type)
            }

            val response =
                client.get(getRequest, requestOptions)

            val sourceAsBytes = response.sourceAsBytes
            if (sourceAsBytes != null) {
                val currentValue = modelReaderAndWriter.deserialize(sourceAsBytes)
                val transformed = transformFunction.invoke(currentValue)
                val indexResponse = index(
                    id = id,
                    obj = transformed,
                    create = false,
                    seqNo = response.seqNo,
                    primaryTerm = response.primaryTerm,
                    refreshPolicy = refreshPolicy,
                    pipeline = pipeline
                )
                if (tries > 0) {
                    // if you start seeing this a lot, you have a lot of concurrent updates to the same thing; not good
                    logger.warn { "retry update $id succeeded after tries=$tries" }
                }
                return indexResponse
            } else {
                throw IllegalStateException("id $id not found")
            }
        } catch (e: ElasticsearchStatusException) {

            if (e.status().status == 409) {
                if (tries < maxUpdateTries) {
                    // we got a version conflict, retry after sleeping a bit (without this failures are more likely
                    Thread.sleep(Random.nextLong(50,500))
                    return update(
                        tries = tries + 1,
                        id = id,
                        transformFunction = transformFunction,
                        maxUpdateTries = maxUpdateTries,
                        refreshPolicy = refreshPolicy,
                        requestOptions = requestOptions,
                        pipeline = pipeline
                    )
                } else {
                    throw IllegalStateException("update of $id failed after $tries attempts")
                }
            } else {
                // something else is wrong
                throw e
            }
        }
    }

    /**
     * Deletes the object object identified by `id`.
     */
    @Suppress("DEPRECATION")
    fun delete(id: String,
               refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.NONE,
               requestOptions: RequestOptions = this.defaultRequestOptions) {
        val deleteRequest = DeleteRequest().index(indexWriteAlias).id(id)

        if (!type.isNullOrBlank()) {
            deleteRequest.type(type)
        }

        deleteRequest.refreshPolicy = refreshPolicy


        client.delete(deleteRequest, requestOptions)
    }

    /**
     * Returns the deserialized [T] for the document identified by `id`.
     */

    fun get(id: String): T? {
        return getWithGetResponse(id)?.first
    }

    /**
     * Returns a `Pair` of the deserialized [T] and the `GetResponse` with all the relevant metadata.
     */
    @Suppress("DEPRECATION")
    fun getWithGetResponse(
        id: String,
        requestOptions: RequestOptions = this.defaultRequestOptions
    ): Pair<T, GetResponse>? {
        val getRequest = GetRequest().index(indexReadAlias).id(id)
        if (fetchSourceContext != null) {
            getRequest.fetchSourceContext(fetchSourceContext)
        }
        if (!type.isNullOrBlank()) {
            getRequest.type(type)
        }

        val response = client.get(getRequest, requestOptions)
        val sourceAsBytes = response.sourceAsBytes

        if (sourceAsBytes != null) {
            val deserialized = modelReaderAndWriter.deserialize(sourceAsBytes)

            return Pair(deserialized, response)
        }
        return null
    }

    /**
     * Create a [BulkIndexingSession] and use it with the [operationsBlock]. Inside the block you can call operations
     * like `index` and other functions exposed by [BulkIndexingSession]. The resulting bulk
     * operations are automatically grouped in bulk requests of size [bulkSize] and sent off to Elasticsearch. For each
     * operation there will be a call to the [itemCallback]. This allows you to keep track of failures, do logging,
     * or implement retries. If you leave this `null`, the default callback implementation defined in
     * [BulkIndexingSession] is used.
     **
     * See [BulkIndexingSession] for the meaning of the other parameters.
     *
     */
    fun bulk(
        bulkSize: Int = 100,
        retryConflictingUpdates: Int = 0,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL,
        itemCallback: ((BulkOperation<T>, BulkItemResponse) -> Unit)? = null,
        operationsBlock: BulkIndexingSession<T>.(session: BulkIndexingSession<T>) -> Unit
    ) {
        val indexer = bulkIndexer(
            bulkSize = bulkSize,
            retryConflictingUpdates = retryConflictingUpdates,
            refreshPolicy = refreshPolicy,
            itemCallback = itemCallback
        )
        // autocloseable so we flush all the items ...
        indexer.use {
            operationsBlock.invoke(indexer, indexer)
        }
    }

    /**
     * Returns a [BulkIndexingSession] for this repository. See [BulkIndexingSession] for the meaning of the other parameters.
     */
    fun bulkIndexer(
        bulkSize: Int = 100,
        retryConflictingUpdates: Int = 0,
        refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL,
        itemCallback: ((BulkOperation<T>, BulkItemResponse) -> Unit)? = null,
        requestOptions: RequestOptions = this.defaultRequestOptions
    ) = BulkIndexingSession(
        client,
        this,
        modelReaderAndWriter,
        bulkSize,
        retryConflictingUpdates = retryConflictingUpdates,
        refreshPolicy = refreshPolicy,
        itemCallback = itemCallback,
        defaultRequestOptions = requestOptions
    )

    /**
     * Call the refresh API on elasticsearch. You should not use this other than in tests. E.g. when testing search
     * queries, you often want to refresh after indexing before calling search
     *
     * Throws UnsupportedOperationException if you do not explicitly opt in to this by setting the `refreshAllowed` to
     * true when creating the repository.
     */
    fun refresh() {
        if (refreshAllowed) {
            // calling this is not safe in production settings but highly useful in tests
            client.lowLevelClient.performRequest(Request("POST", "/$indexWriteAlias/_refresh"))
        } else {
            throw UnsupportedOperationException("refresh is not allowed; you need to opt in by setting refreshAllowed to true")
        }
    }

    /**
     * Perform a search against your index. This creates a `SearchRequest` that is passed into the [block] so you can
     * customize it. Inside the [block] you can manipulate the request to set a `source` and other parameters.
     *
     * Returns a [SearchResults] instance that you can use to get the deserialized results or the raw response.
     *
     * If you want to perform a scrolling search, all you have to do is set [scrolling] to true (default is false).
     * You can also set a [scrollTtlInMinutes] if you want something else than the default of 1 minute.
     */
    fun search(
        scrolling: Boolean = false,
        scrollTtlInMinutes: Long = 1,
        requestOptions: RequestOptions = this.defaultRequestOptions,
        block: SearchRequest.() -> Unit
    ): SearchResults<T> {
        val wrappedBlock: SearchRequest.() -> Unit = {
            this.indices(indexReadAlias)
            if (scrolling) {
                scroll(TimeValue.timeValueMinutes(scrollTtlInMinutes))
            }

            block.invoke(this)

            if (fetchSourceContext != null && source()?.fetchSource() == null) {
                source()?.fetchSource(fetchSourceContext)
            }
        }

        val searchResponse = client.search(requestOptions, wrappedBlock)
        return if (searchResponse.scrollId == null) {
            PagedSearchResults(searchResponse, modelReaderAndWriter)
        } else {
            ScrollingSearchResults(
                searchResponse,
                modelReaderAndWriter,
                client,
                scrollTtlInMinutes,
                requestOptions
            )
        }
    }

    /**
     * Bypass the HighLevelClient XContent juggling and execute a raw json search. The response is parsed back into a SearchResponse.
     *
     * Note, this is workaround for limitation with the Elastic Java client which seems to not support the geo_shape query and refuses to send the request.
     *
     * The actual query runs fine on Elasticsearch; so that is not the problem. This completely bypasses client side parsing of the query.
     *
     * Warning: this function is experimental and may change or be removed in the future.
     */
    fun jsonSearch(
        json: String,
        requestOptions: RequestOptions = this.defaultRequestOptions
    ): AsyncSearchResults<T> {
        val searchResp = client.searchDirect(indexReadAlias, json, requestOptions)
        return AsyncSearchResults(client, modelReaderAndWriter, 1, searchResp, requestOptions)
    }

    fun mSearch(requestOptions: RequestOptions = this.defaultRequestOptions, block: MultiSearchDSL.()->Unit): MultiSearchResults<T> {
        val resp = client.multiSearch(indexName, requestOptions, block)
        return MultiSearchResults(modelReaderAndWriter, resp)
    }

    fun jsonMSearch(
        json: String,
        requestOptions: RequestOptions = this.defaultRequestOptions
    ): MultiSearchResults<T> {
        val searchResp = client.mSearchDirect(indexReadAlias, json, requestOptions)
        return MultiSearchResults(modelReaderAndWriter,searchResp)
    }

    fun count(requestOptions: RequestOptions = this.defaultRequestOptions, block: CountRequest.() -> Unit = {}): Long {
        val request = CountRequest(indexReadAlias)
        block.invoke(request)
        val response = client.count(request, requestOptions)
        return response.count
    }
}
