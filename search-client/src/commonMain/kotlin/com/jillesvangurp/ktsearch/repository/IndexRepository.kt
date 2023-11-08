package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.VariantRestriction
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class RetryingBulkHandler<T : Any>(
    private val updateFunctions: MutableMap<String, (T) -> T>,
    private val indexRepository: IndexRepository<T>,
    private val parentBulkItemCallBack: BulkItemCallBack? = null,
    private val maxRetries: Int = 2,
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
                            item.id, maxRetries = maxRetries, block = updateFunction
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
    suspend fun update(getDocumentResponse: SourceInformation, updateBlock: (T) -> T)

    suspend fun update(
        id: String,
        original: T,
        ifSeqNo: Int,
        ifPrimaryTerm: Int,
        updateBlock: (T) -> T,
    )
}

internal class BulkUpdateSession<T : Any>(
    private val indexRepository: IndexRepository<T>,
    private val updateFunctions: MutableMap<String, (T) -> T>,
    private val bulkSession: DefaultBulkSession,
) : TypedDocumentIBulkSession<T>, BulkSession by bulkSession {

    override suspend fun update(getDocumentResponse: SourceInformation, updateBlock: (T) -> T) {
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
        ifSeqNo: Int,
        ifPrimaryTerm: Int,
        updateBlock: (T) -> T,
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
            index = indexRepository.indexWriteAlias,
            ifSeqNo = ifSeqNo,
            ifPrimaryTerm = ifPrimaryTerm
        )
    }
}

@Suppress("unused")
class IndexRepository<T : Any>(
    val indexName: String,
    private val client: SearchClient,
    val serializer: ModelSerializationStrategy<T>,
    val indexWriteAlias: String = indexName,
    val indexReadAlias: String = indexWriteAlias,
    val defaultParameters: Map<String, String>? = null,
    private val defaultRefresh: Refresh? = Refresh.WaitFor,
    private val defaultTimeout: Duration? = null,
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

//    suspend fun updateIndexMapping(): Unit = TODO()
//    suspend fun updateIndexSettings(): Unit = TODO()

    suspend fun deleteIndex(
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        extraParameters: Map<String, String>? = null,
    ) {
        client.deleteIndex(indexName, masterTimeOut, timeout, combineParams(extraParameters))
    }

//    suspend fun getALiases(): Unit = TODO()


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
            val (doc, _) =  get(id)
            doc
        } catch (e: RestException) {
            if(e.status == 404) {
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
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
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
            target = indexWriteAlias,
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
        maxRetries: Int = 3,
        pipeline: String? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        block: (T) -> T
    ): Pair<T, DocumentIndexResponse> =
        update(
            id = id,
            attempt = 0,
            maxRetries = maxRetries,
            pipeline = pipeline,
            refresh = refresh ?: defaultRefresh,
            routing = routing,
            timeout = timeout ?: defaultTimeout,
            block = block
        )

    private suspend fun update(
        id: String,
        attempt: Int = 0,
        maxRetries: Int = 3,
        pipeline: String? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        block: (T) -> T
    ): Pair<T, DocumentIndexResponse> {
        val (original, resp) = get(id, extraParameters = defaultParameters)

        val updated = block.invoke(original)

        return try {
            updated to client.indexDocument(
                target = indexWriteAlias,
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
            if (e.status == 409 && attempt < maxRetries) {
                return update(id = id, attempt = attempt + 1, maxRetries = maxRetries, block = block)
            } else {
                throw e
            }
        }
    }

    suspend fun delete(
        id: String,
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        version: Long? = null,
        versionType: VersionType? = null,
        waitForActiveShards: String? = null,
        extraParameters: Map<String, String>? = null,
    ): DocumentIndexResponse {
        return client.deleteDocument(
            target = indexWriteAlias,
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
        retryTimeout: Duration = 1.minutes,
        block: suspend TypedDocumentIBulkSession<T>.() -> Unit
    ) {

        val updateFunctions = mutableMapOf<String, (T) -> T>()
        val retryCallback = RetryingBulkHandler(updateFunctions, this, callBack, maxRetries)
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
            target = indexWriteAlias
        )

        val updatingBulkSession = BulkUpdateSession(this, updateFunctions, session)
        block.invoke(updatingBulkSession)
        session.flush()
        retryCallback.awaitJobCompletion(refresh = session.refresh, timeout = retryTimeout)
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
            extraParameters = extraParameters
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

    suspend fun deleteByQuery(block: SearchDSL.() -> Unit): DeleteByQueryResponse {
        return client.deleteByQuery(target = indexWriteAlias, block = block)
    }

    fun deserialize(response: SearchResponse) =
        response.hits?.hits?.map {
            it.source?.let { source -> serializer.deSerialize(source) }
                ?: error("cannot deserialize because hit has no source!")
        } ?: listOf()

    @VariantRestriction(SearchEngineVariant.ES7, SearchEngineVariant.ES8)
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
}

fun <T : Any> SearchClient.repository(
    indexName: String,
    serializer: KSerializer<T>,
    indexWriteAlias: String = indexName,
    indexReadAlias: String = indexWriteAlias,
    defaultParameters: Map<String, String>? = null,

    ) = IndexRepository(
    indexName = indexName,
    client = this,
    serializer = this.ktorModelSerializer(serializer),
    indexWriteAlias = indexWriteAlias,
    indexReadAlias = indexReadAlias,
    defaultParameters = defaultParameters
)
