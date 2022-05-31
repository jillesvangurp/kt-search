package com.jillesvangurp.ktsearch.repository

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlin.time.Duration

class IndexRepository<T : Any>(
    val indexName: String,
    private val client: SearchClient,
    val serializer: ModelSerializationStrategy<T>,
    val indexWriteAlias: String = indexName,
    val indexReadAlias: String = indexWriteAlias,
    val defaultParameters: Map<String, String>? = null,
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
        block: IndexSettingsAndMappingsDSL.() -> Unit,
        waitForActiveShards: Int? = null,
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        extraParameters: Map<String, String>? = null,

        ): IndexCreateResponse {
        val dsl = IndexSettingsAndMappingsDSL()
        block.invoke(dsl)
        return client.createIndex(
            name = indexName,
            mapping = dsl,
            waitForActiveShards = waitForActiveShards,
            masterTimeOut = masterTimeOut,
            timeout = timeout,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun updateIndexMapping(): Unit = TODO()
    suspend fun updateIndexSettings(): Unit = TODO()

    suspend fun deleteIndex(
        masterTimeOut: Duration? = null,
        timeout: Duration? = null,
        extraParameters: Map<String, String>? = null,
    ) {
        client.deleteIndex(indexName, masterTimeOut, timeout, combineParams(extraParameters))
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
            refresh = refresh,
            routing = routing,
            timeout = timeout,
            version = version,
            versionType = versionType,
            waitForActiveShards = waitForActiveShards,
            requireAlias = requireAlias,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun update(id: String, maxRetries: Int = 3, block: (T) -> T): Pair<T, DocumentIndexResponse> =
        update(attempt = 0, maxRetries = maxRetries, id = id, block = block)

    private suspend fun update(
        id: String,
        attempt: Int = 0,
        maxRetries: Int = 3,
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
                ifPrimaryTerm = resp.primaryTerm
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
            refresh = refresh,
            routing = routing,
            timeout = timeout,
            version = version,
            versionType = versionType,
            waitForActiveShards = waitForActiveShards,
            extraParameters = combineParams(extraParameters)
        )
    }

    suspend fun getALiases(): Unit = TODO()
}