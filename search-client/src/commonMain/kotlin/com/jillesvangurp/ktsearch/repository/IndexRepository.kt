package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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
        version: Int? = null,
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
        ).let {
            serializer.deSerialize(it.source) to it
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
        version: Int? = null,
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
        version: Int? = null,
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
        refresh: Refresh? = null,
        routing: String? = null,
        timeout: Duration? = null,
        waitForActiveShards: String? = null,
        requireAlias: Boolean? = null,
        source: String? = null,
        sourceExcludes: String? = null,
        sourceIncludes: String? = null,
        failOnFirstError: Boolean = false,
        callBack: BulkItemCallBack? = null,
        block: suspend BulkSession.() -> Unit
    ) {
        val session = BulkSession(
            searchClient = client,
            failOnFirstError = failOnFirstError,
            callBack = callBack,
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
        block.invoke(session)
        session.flush()
    }

    suspend fun search(rawJson: String): SearchResponse {
        return client.search(target = indexReadAlias, rawJson = rawJson)
    }
    suspend fun search(dsl: SearchDSL): SearchResponse {
        return client.search(target = indexReadAlias, dsl = dsl)
    }

    suspend fun search(block: SearchDSL.() -> Unit): SearchResponse {
        return client.search(target = indexReadAlias, block = block)
    }

    suspend fun searchAfter(keepAlive: Duration= 1.minutes, block: SearchDSL.() -> Unit): Pair<SearchResponse, Flow<SearchResponse.Hit>> {
        return client.searchAfter(target = indexReadAlias, keepAlive = keepAlive, block = block)
    }
}

fun <T : Any> SearchClient.repository(
    indexName: String,
    serializer: KSerializer<T>,
    indexWriteAlias: String = indexName,
    indexReadAlias: String = indexWriteAlias,
    defaultParameters: Map<String, String>? = null,

    ) = IndexRepository<T>(
    indexName = indexName,
    client = this,
    serializer = this.ktorModelSerializer(serializer),
    indexWriteAlias = indexWriteAlias,
    indexReadAlias = indexReadAlias,
    defaultParameters = defaultParameters
)