package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Duration

interface SourceInformation {
    val id: String
    val seqNo: Int?
    val primaryTerm: Int?
    val version: Long?
    val source: JsonObject?
}
inline fun <reified T> SourceInformation.document(json: Json = DEFAULT_JSON) = json.decodeFromJsonElement<T>(source?: error("cannot decode null source"))

@Serializable
data class DocumentIndexResponse(
    @SerialName("_index")
    val index: String,
    @SerialName("_type")
    val type: String?,
    @SerialName("_id")
    override val id: String,
    @SerialName("_version")
    override val version: Long,
    val result: String,
    @SerialName("_shards")
    val shards: Shards,
    @SerialName("_seq_no")
    override val seqNo: Int,
    @SerialName("_primary_term")
    override val primaryTerm: Int,
    @SerialName("_source")
    override val source: JsonObject? = null,
): SourceInformation

suspend inline fun <reified T> SearchClient.indexDocument(
    target: String,
    document: T,
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
    json: Json = DEFAULT_JSON
): DocumentIndexResponse {
    val serialized = json.encodeToString(document)
    return indexDocument(
        target = target,
        serializedJson = serialized,
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
        extraParameters = extraParameters
    )
}

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
    version: Long? = null,
    versionType: VersionType? = null,
    waitForActiveShards: String? = null,
    requireAlias: Boolean? = null,
    extraParameters: Map<String, String>? = null,
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
    override val id: String,
    @SerialName("_version")
    override val version: Long?,
    @SerialName("_source")
    override val source: JsonObject?,
    @SerialName("_seq_no")
    override val seqNo: Int?,
    @SerialName("_primary_term")
    override val primaryTerm: Int?,
    val found: Boolean,
    @SerialName("_routing")
    val routing: String? = null,
    val fields: JsonObject? = null,
): SourceInformation

suspend fun SearchClient.deleteDocument(
    target: String,
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
    return restClient.delete {
        path(target, "_doc", id)

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
    version: Long? = null,
    versionType: VersionType? = null,
    extraParameters: Map<String, String>? = null,
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


class MGetRequest : JsonDsl() {
    class MGetDoc : JsonDsl() {
        var id by property<String>(customPropertyName = "_id")
        var index by property<String>(customPropertyName = "_index")
        var routing by property<String>()
        var source by property<Boolean>(customPropertyName = "_source")
        // documented as working but don't actually work in 8.9.0,
        // https://github.com/elastic/elasticsearch/issues/98310
//        var sourceInclude by property<List<String>>()
//        var sourceExclude by property<List<String>>()
    }

    var ids by property<List<String>>()
    fun doc(docBlock: MGetDoc.() -> Unit) {
        MGetDoc().apply(docBlock).let { doc ->
            if (!this.containsKey("docs")) {
                this["docs"] = mutableListOf<MGetDoc>()
            }
            this.get("docs")?.let { docs ->
                @Suppress("UNCHECKED_CAST")
                docs as MutableList<MGetDoc>
                docs.add(doc)
            }
        }
    }
}

@Serializable
data class MGetResponse(val docs: List<GetDocumentResponse>)

inline fun <reified T> MGetResponse.documents(json: Json = DEFAULT_JSON) = docs.filter { it.found }.map { it.document<T>(json) }

suspend fun SearchClient.mGet(
    index: String? = null,
    preference: String? = null,
    realtime: Boolean? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    storedFields: String? = null,
    source: String? = null,
    // documented as working but don't actually work in 8.9.0,
    // https://github.com/elastic/elasticsearch/issues/98310

//    sourceExcludes: String? = null,
//    sourceIncludes: String? = null,
    block: MGetRequest.() -> Unit
): MGetResponse {
    val request = MGetRequest().apply(block)
    return restClient.post {
        path(index, "_mget")
        parameter("preference", preference)
        parameter("realtime", realtime)
        parameter("refresh", refresh)
        parameter("routing", routing)
        parameter("stored_fields", storedFields)
        parameter("source", source)
//        parameter("source_excludes", sourceExcludes)
//        parameter("source_includes", sourceIncludes)
        json(request, pretty = false)
    }.parse(MGetResponse.serializer())
}
