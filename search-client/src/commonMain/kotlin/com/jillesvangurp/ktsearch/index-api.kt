package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

@Serializable
data class IndexCreateResponse(
    val acknowledged: Boolean,
    @SerialName("shards_acknowledged")
    val shardsAcknowledged: Boolean,
    val index: String
)

suspend fun SearchClient.createIndex(
    name: String,
    mapping: IndexSettingsAndMappingsDSL,
    waitForActiveShards: Int? = null,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null
): IndexCreateResponse {
    return restClient.put {
        path(name)

        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("master_timeout", masterTimeOut)
        parameter("timeout", timeout)

        json(mapping)
    }.parse(IndexCreateResponse.serializer(), json)
}

suspend fun SearchClient.createIndex(
    name: String,
    waitForActiveShards: Int? = null,
    masterTimeOut: Duration? = null,
    timeout: Duration? = null,
    block: IndexSettingsAndMappingsDSL.() -> Unit
): IndexCreateResponse {
    val dsl = IndexSettingsAndMappingsDSL()
    block.invoke(dsl)

    return createIndex(name,dsl,waitForActiveShards,masterTimeOut,timeout)
}