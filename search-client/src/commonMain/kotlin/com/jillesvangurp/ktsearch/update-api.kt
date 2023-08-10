package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.Script
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

@Serializable
data class DocumentUpdateResponse(
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
    val primaryTerm: Int,
    val get: DocumentUpdateResponse.UpdatedSourceInformation?=null
) {
    @Serializable
    data class UpdatedSourceInformation(
        @SerialName("_source")
        val source: JsonObject?,
        @SerialName("_seq_no")
        val seqNo: Int?,
        @SerialName("_primary_term")
        val primaryTerm: Int?,
        val version: Long?,
        val found: Boolean,
        @SerialName("_routing")
        val routing: String? = null,
    )
}

suspend inline fun <reified T> SearchClient.updateDocument(
    target: String,
    id: String,
    doc: T,
    json: Json = DEFAULT_JSON,
    detectNoop: Boolean? = null,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    requireAlias: Boolean? = null,
    retryOnConflict: Int? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    source: String? = null,
    sourceIncludes: String? = null,
    sourceExcludes: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    extraParameters: Map<String, String>? = null,
): DocumentUpdateResponse = updateDocument(
    target = target,
    id = id,
    docJson = json.encodeToString(doc),
    detectNoop = detectNoop,
    ifSeqNo = ifSeqNo,
    ifPrimaryTerm = ifPrimaryTerm,
    requireAlias = requireAlias,
    retryOnConflict = retryOnConflict,
    refresh = refresh,
    routing = routing,
    source = source,
    sourceIncludes = sourceIncludes,
    sourceExcludes = sourceExcludes,
    timeout = timeout,
    waitForActiveShards = waitForActiveShards,
    extraParameters = extraParameters
)

suspend fun SearchClient.updateDocument(
    target: String,
    id: String,
    docJson: String,
    detectNoop: Boolean? = null,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    requireAlias: Boolean? = null,
    retryOnConflict: Int? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    source: String? = null,
    sourceIncludes: String? = null,
    sourceExcludes: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    extraParameters: Map<String, String>? = null,
): DocumentUpdateResponse {
    return restClient.post {
        path(target, "_update", id)

        parameter("if_seq_no", ifSeqNo)
        parameter("if_primary_term", ifPrimaryTerm)
        parameter("require_alias", requireAlias)
        parameter("retry_on_conflict", retryOnConflict)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("_source", source)
        parameter("_source_includes", sourceIncludes)
        parameter("_source_excludes", sourceExcludes)
        parameter("timeout", timeout)
        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("require_alias", requireAlias)
        parameters(extraParameters)
        rawBody("""{"doc": $docJson${detectNoop?.let { """, "detect_noop":$detectNoop""" } ?: ""}}""".trimIndent())
    }.parse(DocumentUpdateResponse.serializer(), json)
}

suspend inline fun <reified T> SearchClient.updateDocument(
    target: String,
    id: String,
    script: Script,
    upsertJson: T,
    json: Json = DEFAULT_JSON,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    requireAlias: Boolean? = null,
    retryOnConflict: Int? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    source: String? = null,
    sourceIncludes: String? = null,
    sourceExcludes: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    extraParameters: Map<String, String>? = null,
) = updateDocument(
    target = target,
    id = id,
    script = script,
    upsertJson = json.encodeToString(upsertJson),
    ifSeqNo = ifSeqNo,
    ifPrimaryTerm = ifPrimaryTerm,
    requireAlias = requireAlias,
    retryOnConflict = retryOnConflict,
    refresh = refresh,
    routing = routing,
    source = source,
    sourceIncludes = sourceIncludes,
    sourceExcludes = sourceExcludes,
    timeout = timeout,
    waitForActiveShards = waitForActiveShards,
    extraParameters = extraParameters
)

suspend fun SearchClient.updateDocument(
    target: String,
    id: String,
    script: Script,
    upsertJson: String? = null,
    ifSeqNo: Int? = null,
    ifPrimaryTerm: Int? = null,
    requireAlias: Boolean? = null,
    retryOnConflict: Int? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    source: String? = null,
    sourceIncludes: String? = null,
    sourceExcludes: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    extraParameters: Map<String, String>? = null,
): DocumentUpdateResponse {
    return restClient.post {
        path(target, "_update", id)

        parameter("if_seq_no", ifSeqNo)
        parameter("if_primary_term", ifPrimaryTerm)
        parameter("require_alias", requireAlias)
        parameter("retry_on_conflict", retryOnConflict)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("_source", source)
        parameter("_source_includes", sourceIncludes)
        parameter("_source_excludes", sourceExcludes)
        parameter("timeout", timeout)
        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("require_alias", requireAlias)
        parameters(extraParameters)
        rawBody("""{"script": ${script.json()}${upsertJson?.let { """, "upsert":$upsertJson""" } ?: ""}}""".trimIndent())
    }.parse(DocumentUpdateResponse.serializer(), json)
}
