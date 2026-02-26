@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

@Serializable
data class IndexCreateResponse(
    val acknowledged: Boolean,
    @SerialName("shards_acknowledged")
    val shardsAcknowledged: Boolean,
    val index: String
)

@Serializable
data class RefreshResponse(@SerialName("_shards") val shards: Shards)

suspend fun SearchClient.createIndex(
    name: String,
    mappingAndSettings: String,
    waitForActiveShards: Int? = null,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null,
    extraParameters: Map<String, String>? = null,

    ): IndexCreateResponse {
    return restClient.put {
        path(name)

        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("master_timeout", masterTimeOut)
        parameter("timeout", timeout)
        parameters(extraParameters)
        rawBody(mappingAndSettings)
    }.parse(IndexCreateResponse.serializer(), json)
}

suspend fun SearchClient.createIndex(
    name: String,
    mapping: IndexSettingsAndMappingsDSL,
    waitForActiveShards: Int? = null,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null,
    extraParameters: Map<String, String>? = null,

    ): IndexCreateResponse {
    return restClient.put {
        path(name)

        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("master_timeout", masterTimeOut)
        parameter("timeout", timeout)
        parameters(extraParameters)
        json(mapping)
    }.parse(IndexCreateResponse.serializer(), json)
}

suspend fun SearchClient.createIndex(
    name: String,
    waitForActiveShards: Int? = null,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null,
    extraParameters: Map<String, String>? = null,
    block: (IndexSettingsAndMappingsDSL.() -> Unit)?=null
): IndexCreateResponse {
    val dsl = IndexSettingsAndMappingsDSL()
    block?.invoke(dsl)

    return createIndex(
        name = name,
        mapping = dsl,
        waitForActiveShards = waitForActiveShards,
        masterTimeOut = masterTimeOut,
        timeout = timeout,
        extraParameters = extraParameters
    )
}

suspend fun SearchClient.deleteIndex(
    target: String,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null,
    ignoreUnavailable: Boolean? = null,
    extraParameters: Map<String, String>? = null,
): JsonObject = restClient.delete {
    path(target)

    parameter("master_timeout", masterTimeOut)
    parameter("timeout", timeout)
    parameter("ignore_unavailable", ignoreUnavailable)
    parameters(extraParameters)
}.parseJsonObject()

/** `POST /{target?}/_refresh`: make recent index operations searchable. */
suspend fun SearchClient.refresh(
    target: String? = null,
    allowNoIndices: Boolean? = null,
    expandWildcards: ExpandWildCards? = null,
    ignoreUnavailable: Boolean? = null,
    extraParameters: Map<String, String>? = null,
): RefreshResponse {
    return restClient.post {
        path(*listOfNotNull(target.takeIf { !it.isNullOrBlank() }, "_refresh").toTypedArray())

        parameter("allow_no_indices", allowNoIndices)
        parameter("expand_wildcards", expandWildcards)
        parameter("ignore_unavailable", ignoreUnavailable)
        parameters(extraParameters)
    }.parse(RefreshResponse.serializer(), json)
}

suspend fun SearchClient.getIndex(name: String): JsonObject {
    return restClient.get {
        path(name)
    }.parseJsonObject()
}

suspend fun SearchClient.getIndexMappings(name: String): JsonObject {
    return restClient.get {
        path(name,"_mappings")
    }.parseJsonObject()
}

/** `PUT /{name}/_mapping`: update mappings for an existing index. */
suspend fun SearchClient.putIndexMappings(name: String, mappings: String): JsonObject {
    return restClient.put {
        path(name, "_mapping")
        rawBody(mappings)
    }.parseJsonObject()
}
