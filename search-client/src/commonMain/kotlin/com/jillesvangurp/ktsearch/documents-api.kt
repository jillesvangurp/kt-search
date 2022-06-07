package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.camelCase2SnakeCase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration

@Serializable
data class DocumentIndexResponse(
    @SerialName("_index")
    val index: String,
    @SerialName("_type")
    val type: String?,
    @SerialName("_id")
    val id: String,
    @SerialName("_version")
    val version: Long,
    val result: String,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("_seq_no")
    val seqNo: Int,
    @SerialName("_primary_term")
    val primaryTerm: Int
)

suspend fun SearchClient.indexDocument(
    target: String,
    serializedJson: String,
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
    extraParameters: Map<String,String>?=null,
    ): DocumentIndexResponse {
    return restClient.post {
        if (id == null) {
            path(target, "_doc")
        } else {
            path(target, "_doc", id)
        }

        parameter("if_seq_no", ifSeqNo)
        parameter("if_primary_term", ifPrimaryTerm)
        parameter("op_type", opType)
        parameter("pipeline", pipeline)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("timeout", timeout)
        parameter("version", version)
        parameter("version_type", versionType)
        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("require_alias", requireAlias)
        parameters(extraParameters)
        rawBody(serializedJson)
    }.parse(DocumentIndexResponse.serializer(), json)
}

@Serializable
data class GetDocumentResponse(
    @SerialName("_index")
    val index: String,
    @SerialName("_type")
    val type: String?,
    @SerialName("_id")
    val id: String,
    @SerialName("_version")
    val version: Long,
    @SerialName("_source")
    val source: JsonObject,
    @SerialName("_seq_no")
    val seqNo: Int,
    @SerialName("_primary_term")
    val primaryTerm: Int,
    val found: Boolean,
    @SerialName("_routing")
    val routing: String? = null,
    val fields: JsonObject? = null,
) {
    inline fun <reified T> document(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(source)
}

suspend fun SearchClient.deleteDocument(
    target: String,
    id: String,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    timeout: Duration? = null,
    version: Int? = null,
    versionType: VersionType? = null,
    waitForActiveShards: String? = null,
    extraParameters: Map<String,String>?=null,
    ): DocumentIndexResponse {
    return restClient.delete {
        path(target, "_doc",id)

        parameter("if_seq_no", ifSeqNo)
        parameter("if_primary_term", ifPrimaryTerm)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("timeout", timeout)
        parameter("version", version)
        parameter("version_type", versionType)
        parameter("wait_for_active_shards", waitForActiveShards)
        parameters(extraParameters)

    }.parse(DocumentIndexResponse.serializer(), json)
}

suspend fun SearchClient.getDocument(
    target: String,
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
    extraParameters: Map<String,String>?=null,
    ): GetDocumentResponse {
    return restClient.get {
        path(target, "_doc", id)

        parameter("preference", preference)
        parameter("realtime", realtime)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("stored_fields", storedFields)
        parameter("source", source)
        parameter("source_excludes", sourceExcludes)
        parameter("source_includes", sourceIncludes)
        parameter("version", version)
        parameter("version_type", versionType)
        parameters(extraParameters)
    }.parse(GetDocumentResponse.serializer(), json)
}
