package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.BulkItemCallBack
import com.jillesvangurp.ktsearch.BulkResponse
import com.jillesvangurp.ktsearch.BulkSession
import com.jillesvangurp.ktsearch.DefaultBulkSession
import com.jillesvangurp.ktsearch.DeleteByQueryResponse
import com.jillesvangurp.ktsearch.DocumentIndexResponse
import com.jillesvangurp.ktsearch.ExpandWildCards
import com.jillesvangurp.ktsearch.GetDocumentResponse
import com.jillesvangurp.ktsearch.IndexCreateResponse
import com.jillesvangurp.ktsearch.MGetRequest
import com.jillesvangurp.ktsearch.MGetResponse
import com.jillesvangurp.ktsearch.MsearchRequest
import com.jillesvangurp.ktsearch.MultiSearchResponse
import com.jillesvangurp.ktsearch.OperationType
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchOperator
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.SearchType
import com.jillesvangurp.ktsearch.SourceInformation
import com.jillesvangurp.ktsearch.SuggestMode
import com.jillesvangurp.ktsearch.VersionType
import com.jillesvangurp.ktsearch.createIndex
import com.jillesvangurp.ktsearch.deleteByQuery
import com.jillesvangurp.ktsearch.deleteDocument
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.getDocument
import com.jillesvangurp.ktsearch.getIndexesForAlias
import com.jillesvangurp.ktsearch.indexDocument
import com.jillesvangurp.ktsearch.mGet
import com.jillesvangurp.ktsearch.msearch
import com.jillesvangurp.ktsearch.parseHits
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.searchAfter
import com.jillesvangurp.ktsearch.updateAliases
import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.VariantRestriction
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer

private val logger = KotlinLogging.logger { }

internal class RetryingBulkHandler<T : Any>(
    private val updateFunctions: MutableMap<String, suspend (T) -> T>,
    private val indexRepository: IndexRepository<T>,
    private val parentBulkItemCallBack: BulkItemCallBack? = null,
    private val maxRetries: Int = 2,
    private val retryDelay: Duration = 2.seconds,
    private val updateScope: CoroutineScope = CoroutineScope(CoroutineName("bulk-update"))
) : BulkItemCallBack {
    private var count = 0
    override fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails) {
        if (operationType == OperationType.Index && item.error?.type == "version_conflict_engine_exception") {
            updateFunctions[item.id]?.let { updateFunction ->
                count++
                val job = updateScope.launch {
                    try {
                        val (_, result) = indexRepository.update(
                            item.id, maxRetries = maxRetries, block = updateFunction, retryDelay = retryDelay
                        )
                        itemOk(
                            operationType, BulkResponse.ItemDetails(
                                index = result.index,
                                type = null,
                                id = item.id,
                                version = result.version,
                                result = result.result,
                                shards = result.shards,
                                seqNo = result.seqNo.toLong(),
                                primaryTerm = result.primaryTerm.toLong(),
                                status = 200 // OK
                            )
                        )
                    } catch (e: Exception) {
                        itemFailed(
                            operationType,
                            item.copy(error = BulkResponse.ItemError(type = "retry_failed", reason = e.message))
                        )
                    }
                }
                job.invokeOnCompletion {
                    count--
                }
            }
        }
        parentBulkItemCallBack?.itemFailed(operationType, item)
    }

    override fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails) {
        updateFunctions.remove(item.id)
        parentBulkItemCallBack?.itemOk(operationType, item)
    }

    override fun bulkRequestFailed(e: Exception, ops: List<Pair<String, String?>>) {
        parentBulkItemCallBack?.bulkRequestFailed(e, ops)
    }

    internal suspend fun awaitJobCompletion(refresh: Refresh?, timeout: Duration = 10.seconds) {
        if (refresh != Refresh.False) {
            withTimeout(timeout) {
                while (count > 0) {
                    delay(20.milliseconds)
                }
            }
        }
    }
}

interface TypedDocumentIBulkSession<T> : BulkSession {
    suspend fun update(getDocumentResponse: SourceInformation, updateBlock: suspend (T) -> T)

    suspend fun update(
        id: String,
        original: T,
        ifSeqNo: Long,
        ifPrimaryTerm: Long,
        updateBlock: suspend (T) -> T,
    )
}

internal class BulkUpdateSession<T : Any>(
    private val indexRepository: IndexRepository<T>,
    private val updateFunctions: MutableMap<String, suspend (T) -> T>,
    private val bulkSession: DefaultBulkSession,
) : TypedDocumentIBulkSession<T>, BulkSession by bulkSession {

    override suspend fun update(getDocumentResponse: SourceInformation, updateBlock: suspend (T) -> T) {
        update(
            id = getDocumentResponse.id,
            // errors only happen if the document does not exist
            original = indexRepository.serializer.deSerialize(
                getDocumentResponse.source ?: error("no document source")
            ),
            ifSeqNo = getDocumentResponse.seqNo ?: error("no seq_no"),
            ifPrimaryTerm = getDocumentResponse.primaryTerm ?: error("no primary_term"),
            updateBlock = updateBlock
        )
    }

    override suspend fun update(
        id: String,
        original: T,
        ifSeqNo: Long,
        ifPrimaryTerm: Long,
        updateBlock: suspend (T) -> T,
    ) {
        if (updateFunctions.containsKey(id)) {
            throw IllegalArgumentException("you can't update the same id $id twice in one session")
        }
        updateFunctions[id] = updateBlock
        val toStore = updateBlock.invoke(original)
        val source = indexRepository.serializer.serialize(toStore)
        bulkSession.index(
            source,
            id = id,
            index = indexRepository.indexNameOrWriteAlias,
            ifSeqNo = ifSeqNo,
            ifPrimaryTerm = ifPrimaryTerm
        )
    }
}

@Suppress("unused")
class IndexRepository<T : Any>(
    val indexNameOrWriteAlias: String,
    val indexReadAlias: String = indexNameOrWriteAlias,
    private val client: SearchClient,
    val serializer: ModelSerializationStrategy<T>,
    val defaultParameters: Map<String, String>? = null,
    private val defaultRefresh: Refresh? = Refresh.WaitFor,
    private val defaultTimeout: Duration? = null,
    private val logging: Boolean = true
) {
    private fun combineParams(extraParameters: Map<String, String>?): Map<String, String>? {
        return extraParameters?.let {
            defaultParameters?.let {
                val mutableMap = defaultParameters.toMutableMap()
                mutableMap.putAll(it)
                mutableMap
            } ?: extraParameters
        } ?: defaultParameters
    }

    suspend fun createIndex(
        indexName: String,
        mappingsAndSettings: IndexSettingsAndMappingsDSL,
        waitForActiveShards: Int? = null,
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        extraParameters: Map<String, String>? = null,

        ): IndexCreateResponse {

        return client.createIndex(
            name = indexName,
            mapping = mappingsAndSettings,
            waitForActiveShards = waitForActiveShards,
            masterTimeOut = masterTimeOut,
            timeout = timeout ?: defaultTimeout,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun createIndex(
        indexName: String,
        waitForActiveShards: Int? = null,
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        extraParameters: Map<String, String>? = null,
        block: IndexSettingsAndMappingsDSL.() -> Unit,
    ): IndexCreateResponse {
        val dsl = IndexSettingsAndMappingsDSL()
        block.invoke(dsl)
        return client.createIndex(
            name = indexName,
            mapping = dsl,
            waitForActiveShards = waitForActiveShards,
            masterTimeOut = masterTimeOut,
            timeout = timeout ?: defaultTimeout,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun deleteIndex(
        indexName: String,
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        ignoreUnavailable: Boolean? = null,
        extraParameters: Map<String, String>? = null,
    ) {
        client.deleteIndex(indexName, masterTimeOut, timeout, ignoreUnavailable, combineParams(extraParameters))
    }

    /**
     * Convenient way to atomically change over to a new index.
     *
     * Assumes there is only one index for the read and write alias. If this is not true, don't use [deleteOldIndex].
     *
     * Important. you should of course take care of reindexing yourself before you call this with [deleteOldIndex] set to true
     */
    suspend fun updateAliasesToNewIndex(newIndex: String, deleteOldIndex: Boolean = false) {
        val oldIndex = client.getIndexesForAlias(indexNameOrWriteAlias).firstOrNull()
        client.updateAliases {
            addAliasForIndex(newIndex, indexReadAlias)
            addAliasForIndex(newIndex, indexNameOrWriteAlias)
            if (oldIndex != null) {
                removeAliasForIndex(oldIndex, indexReadAlias)
                removeAliasForIndex(oldIndex, indexNameOrWriteAlias, deleteOldIndex)
            }
        }
    }

    suspend fun get(
        id: String,
        preference: String? = null,
        realtime: Boolean? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        storedFields: String? = null,
        source: String? = null,
        sourceExcludes: String? = null,
        sourceIncludes: String? = null,
        version: Long? = null,
        versionType: VersionType? = null,
        extraParameters: Map<String, String>? = null,
    ): Pair<T, GetDocumentResponse> {
        return client.getDocument(
            target = indexReadAlias,
            id = id,
            preference = preference,
            realtime = realtime,
            refresh = refresh,
            routing = routing,
            storedFields = storedFields,
            source = source,
            sourceExcludes = sourceExcludes,
            sourceIncludes = sourceIncludes,
            version = version,
            versionType = versionType,
            extraParameters = combineParams(extraParameters)
        ).let { response ->
            serializer.deSerialize(response.source ?: error("no document source")) to response
        }
    }

    /**
     * More user friendly version of [get] that returns either the document or null.
     *
     * Use this if you don't need the `GetResponse`.
     */
    suspend fun getDocument(id: String): T? {
        return try {
            val (doc, _) = get(id)
            doc
        } catch (e: RestException) {
            if (e.status == 404) {
                // not found, just return null
                null
            } else {
                throw e
            }
        }
    }

    suspend fun index(
        value: T,
        id: String? = null,
        ifSeqNo: Long? = null,
        ifPrimaryTerm: Long? = null,
        opType: OperationType? = null,
        pipeline: String? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        version: Long? = null,
        versionType: VersionType? = null,
        waitForActiveShards: String? = null,
        requireAlias: Boolean? = null,
        extraParameters: Map<String, String>? = null,

        ): DocumentIndexResponse {
        return client.indexDocument(
            target = indexNameOrWriteAlias,
            serializedJson = serializer.serialize(value),
            id = id,
            ifSeqNo = ifSeqNo,
            ifPrimaryTerm = ifPrimaryTerm,
            opType = opType,
            pipeline = pipeline,
            refresh = refresh ?: defaultRefresh,
            routing = routing,
            timeout = timeout ?: defaultTimeout,
            version = version,
            versionType = versionType,
            waitForActiveShards = waitForActiveShards,
            requireAlias = requireAlias,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun update(
        id: String,
        maxRetries: Int = 5,
        pipeline: String? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        retryDelay: Duration = 2.seconds,
        block: suspend (T) -> T,
    ): Pair<T, DocumentIndexResponse> =
        update(
            id = id,
            attempt = 0,
            maxRetries = maxRetries,
            pipeline = pipeline,
            refresh = refresh ?: defaultRefresh,
            routing = routing,
            timeout = timeout ?: defaultTimeout,
            retryDelay = retryDelay,
            block = block
        )

    private suspend fun update(
        id: String,
        attempt: Int = 0,
        maxRetries: Int = 3,
        pipeline: String?,
        refresh: Refresh?,
        routing: String?,
        timeout: Duration?,
        retryDelay: Duration,
        block: suspend (T) -> T
    ): Pair<T, DocumentIndexResponse> {
        val (original, resp) = get(id, extraParameters = defaultParameters)

        val updated = block.invoke(original)

        return try {
            updated to client.indexDocument(
                target = indexNameOrWriteAlias,
                id = id,
                serializedJson = serializer.serialize(updated),
                ifSeqNo = resp.seqNo,
                ifPrimaryTerm = resp.primaryTerm,
                pipeline = pipeline,
                refresh = refresh ?: defaultRefresh,
                routing = routing,
                timeout = timeout ?: defaultTimeout,
            )
        } catch (e: RestException) {
            if ((e.status == 409 || e.status == 429) && attempt < maxRetries) {
                if (e.status == 429) {
                    if (logging) {
                        logger.warn { "update is triggering circuit breaker on attempt $attempt (status ${e.status}): ${e.message}" }
                    }
                    // 429 means we're triggering a circuit breaker, so back off before retrying
                    // we've seen this kind of failure a few times.
                    delay(retryDelay)
                }
                return update(
                    id = id,
                    attempt = attempt + 1,
                    maxRetries = maxRetries,
                    pipeline = pipeline,
                    refresh = refresh ?: defaultRefresh,
                    routing = routing,
                    timeout = timeout ?: defaultTimeout,
                    retryDelay = 1.seconds,
                    block = block
                )
                    .also {
                        if (attempt > 0 && logging) {
                            logger.info { "update succeeded on attempt $attempt" }
                        }
                    }
            } else {
                throw e
            }
        }
    }

    suspend fun delete(
        id: String,
        ifSeqNo: Long? = null,
        ifPrimaryTerm: Long? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        version: Long? = null,
        versionType: VersionType? = null,
        waitForActiveShards: String? = null,
        extraParameters: Map<String, String>? = null,
    ): DocumentIndexResponse {
        return client.deleteDocument(
            target = indexNameOrWriteAlias,
            id = id,
            ifSeqNo = ifSeqNo,
            ifPrimaryTerm = ifPrimaryTerm,
            refresh = refresh ?: defaultRefresh,
            routing = routing,
            timeout = timeout ?: defaultTimeout,
            version = version,
            versionType = versionType,
            waitForActiveShards = waitForActiveShards,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun bulk(
        bulkSize: Int = 100,
        pipeline: String? = null,
        refresh: Refresh? = Refresh.WaitFor,
        routing: String? = null,
        timeout: Duration? = null,
        waitForActiveShards: String? = null,
        requireAlias: Boolean? = null,
        source: String? = null,
        sourceExcludes: String? = null,
        sourceIncludes: String? = null,
        failOnFirstError: Boolean = false,
        callBack: BulkItemCallBack? = null,
        maxRetries: Int = 2,
        retryDelay: Duration = 2.seconds,
        retryTimeout: Duration = 1.minutes,
        block: suspend TypedDocumentIBulkSession<T>.() -> Unit
    ) {

        val updateFunctions = mutableMapOf<String, suspend (T) -> T>()
        val retryCallback = RetryingBulkHandler(
            updateFunctions = updateFunctions,
            indexRepository = this,
            parentBulkItemCallBack = callBack,
            maxRetries = maxRetries,
            retryDelay = retryDelay,
        )
        val session = DefaultBulkSession(
            searchClient = client,
            failOnFirstError = failOnFirstError,
            callBack = retryCallback,
            bulkSize = bulkSize,
            pipeline = pipeline,
            refresh = refresh ?: defaultRefresh,
            routing = routing,
            timeout = timeout ?: defaultTimeout,
            waitForActiveShards = waitForActiveShards,
            requireAlias = requireAlias,
            source = source,
            sourceExcludes = sourceExcludes,
            sourceIncludes = sourceIncludes,
            target = indexNameOrWriteAlias
        )

        try {
            val updatingBulkSession = BulkUpdateSession(this, updateFunctions, session)
            block.invoke(updatingBulkSession)
            session.flush()
            retryCallback.awaitJobCompletion(refresh = session.refresh, timeout = retryTimeout)
        } finally {
            session.close()
        }
    }

    suspend fun mGet(
        vararg docIds: String,
        preference: String? = null,
        realtime: Boolean? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        storedFields: String? = null,
        source: String? = null,
    ) = mGet(
        preference = preference,
        realtime = realtime,
        refresh = refresh,
        routing = routing,
        storedFields = storedFields,
        source = source
    ) {
        ids = docIds.toList()
    }

    suspend fun mGet(
        docIds: List<String>,
        preference: String? = null,
        realtime: Boolean? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        storedFields: String? = null,
        source: String? = null,
    ) = mGet(
        preference = preference,
        realtime = realtime,
        refresh = refresh,
        routing = routing,
        storedFields = storedFields,
        source = source
    ) {
        ids = docIds
    }

    suspend fun mGetDocuments(
        docIds: List<String>,
        preference: String? = null,
        realtime: Boolean? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        storedFields: String? = null,
        source: String? = null,
    ) = mGet(
        preference = preference,
        realtime = realtime,
        refresh = refresh,
        routing = routing,
        storedFields = storedFields,
        source = source
    ) {
        ids = docIds
    }.let { resp ->
        resp.docs.mapNotNull { it.source?.let { src -> serializer.deSerialize(src) } }
    }

    suspend fun mGet(
        preference: String? = null,
        realtime: Boolean? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        storedFields: String? = null,
        source: String? = null,
//        sourceExcludes: String? = null,
//        sourceIncludes: String? = null,

        block: MGetRequest.() -> Unit
    ): MGetResponse {
        return client.mGet(
            index = indexReadAlias,
            preference = preference,
            realtime = realtime,
            refresh = refresh,
            routing = routing,
            storedFields = storedFields,
            source = source,
//            sourceExcludes = sourceExcludes,
//            sourceIncludes = sourceIncludes,
            block = block
        )
    }

    suspend fun search(
        rawJson: String,
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
        retries: Int = 3,
        retryDelay: Duration = 2.seconds,
    ): SearchResponse {
        return client.search(
            target = indexReadAlias,
            rawJson = rawJson,
            allowNoIndices = allowNoIndices,
            allowPartialSearchResults = allowPartialSearchResults,
            analyzer = analyzer,
            analyzeWildcard = analyzeWildcard,
            batchedReduceSize = batchedReduceSize,
            ccsMinimizeRoundtrips = ccsMinimizeRoundtrips,
            defaultOperator = defaultOperator,
            df = df,
            docValueFields = docvalueFields,
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
            source = _source,
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
            extraParameters = extraParameters,
            retries = retries,
            retryDelay = retryDelay
        )
    }

    suspend fun search(
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
    ): SearchResponse {
        return client.search(
            target = indexReadAlias,
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
            source = _source,
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

    suspend fun search(
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
        block: SearchDSL.() -> Unit
    ): SearchResponse {
        return client.search(
            target = indexReadAlias,
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
            extraParameters = extraParameters,
            block = block
        )
    }

    /**
     * More user friendly way to search documents that simply returns a list of documents.
     *
     * Use this if you don't need the hit meta data or search response.
     */
    suspend fun searchDocuments(
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
        block: SearchDSL.() -> Unit
    ): List<T> {
        return client.search(
            target = indexReadAlias,
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
            extraParameters = extraParameters,
            block = block
        ).parseHits(serializer)
    }

    suspend fun msearch(
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
        return client.msearch(
            target = indexReadAlias,
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
            typedKeys = typedKeys,
            block = block
        )
    }

    suspend fun deleteByQuery(block: SearchDSL.() -> Unit): DeleteByQueryResponse {
        return client.deleteByQuery(target = indexNameOrWriteAlias, block = block)
    }

    fun deserialize(response: SearchResponse) =
        response.hits?.hits?.map {
            it.source?.let { source -> serializer.deSerialize(source) }
                ?: error("cannot deserialize because hit has no source!")
        } ?: listOf()

    @VariantRestriction(SearchEngineVariant.ES7, SearchEngineVariant.ES8, SearchEngineVariant.ES9)
    suspend fun searchAfter(
        keepAlive: Duration = 1.minutes,
        optInToCustomSort: Boolean = false,
        block: SearchDSL.() -> Unit
    ): Pair<SearchResponse, Flow<SearchResponse.Hit>> {
        return client.searchAfter(
            target = indexReadAlias,
            keepAlive = keepAlive,
            block = block,
            optInToCustomSort = optInToCustomSort
        )
    }

    // FIXME, future variant of this with context receivers would be nice
    fun parse(hits: Flow<SearchResponse.Hit>): Flow<T> =
        hits.mapNotNull { hit ->
            hit.source?.let { src ->
                serializer.deSerialize(src)
            }
        }

    fun parse(hits: List<SearchResponse.Hit>): List<T> =
        hits.mapNotNull { hit ->
            hit.source?.let { src ->
                serializer.deSerialize(src)
            }
        }
}

fun <T : Any> SearchClient.repository(
    indexWriteAlias: String,
    serializer: KSerializer<T>,
    indexReadAlias: String = indexWriteAlias,
    defaultParameters: Map<String, String>? = null,
    defaultRefresh: Refresh? = Refresh.WaitFor,
    defaultTimeout: Duration? = null,
    logging: Boolean = true


) = IndexRepository(
    client = this,
    serializer = this.ktorModelSerializer(serializer),
    indexNameOrWriteAlias = indexWriteAlias,
    indexReadAlias = indexReadAlias,
    defaultParameters = defaultParameters,
    defaultRefresh = defaultRefresh,
    defaultTimeout = defaultTimeout,
    logging = logging,
)
