@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subset response for `GET /_nodes/stats`.
 *
 * Only operationally relevant fields are modeled. Unknown fields are ignored
 * by the configured JSON parser.
 */
@Serializable
data class NodesStatsResponse(
    @SerialName("cluster_name")
    val clusterName: String? = null,
    val nodes: Map<String, NodeStatsNode> = emptyMap(),
)

@Serializable
data class NodeStatsNode(
    val name: String? = null,
    val ip: String? = null,
    val host: String? = null,
    val roles: List<String>? = null,
    val os: NodeStatsOs? = null,
    val process: NodeStatsProcess? = null,
    val jvm: NodeStatsJvm? = null,
    val fs: NodeStatsFs? = null,
    val indices: NodeStatsIndices? = null,
    @SerialName("thread_pool")
    val threadPool: Map<String, NodeStatsThreadPool>? = null,
)

@Serializable
data class NodeStatsOs(
    val cpu: NodeStatsCpu? = null,
)

@Serializable
data class NodeStatsProcess(
    val cpu: NodeStatsCpu? = null,
)

@Serializable
data class NodeStatsCpu(
    val percent: Int? = null,
)

@Serializable
data class NodeStatsJvm(
    val mem: NodeStatsJvmMem? = null,
)

@Serializable
data class NodeStatsJvmMem(
    @SerialName("heap_used_in_bytes")
    val heapUsedInBytes: Long? = null,
    @SerialName("heap_max_in_bytes")
    val heapMaxInBytes: Long? = null,
    @SerialName("heap_used_percent")
    val heapUsedPercent: Int? = null,
)

@Serializable
data class NodeStatsFs(
    val total: NodeStatsFsTotal? = null,
)

@Serializable
data class NodeStatsFsTotal(
    @SerialName("total_in_bytes")
    val totalInBytes: Long? = null,
    @SerialName("available_in_bytes")
    val availableInBytes: Long? = null,
)

@Serializable
data class NodeStatsIndices(
    val docs: NodeStatsDocs? = null,
    val store: NodeStatsStore? = null,
    val segments: NodeStatsSegments? = null,
    val indexing: NodeStatsIndexing? = null,
    val search: NodeStatsSearch? = null,
)

@Serializable
data class NodeStatsDocs(
    val count: Long? = null,
)

@Serializable
data class NodeStatsStore(
    @SerialName("size_in_bytes")
    val sizeInBytes: Long? = null,
)

@Serializable
data class NodeStatsSegments(
    val count: Long? = null,
    @SerialName("memory_in_bytes")
    val memoryInBytes: Long? = null,
)

@Serializable
data class NodeStatsIndexing(
    @SerialName("index_total")
    val indexTotal: Long? = null,
)

@Serializable
data class NodeStatsSearch(
    @SerialName("query_total")
    val queryTotal: Long? = null,
)

@Serializable
data class NodeStatsThreadPool(
    val active: Long? = null,
    val queue: Long? = null,
    val rejected: Long? = null,
)

/**
 * `GET /_nodes/stats`.
 *
 * Use [metrics] to limit endpoint sections and [filterPath] to reduce
 * payload size.
 */
suspend fun SearchClient.nodesStats(
    metrics: List<String>? = null,
    filterPath: List<String>? = null,
    extraParameters: Map<String, String>? = null,
): NodesStatsResponse {
    val pathParts = mutableListOf("_nodes", "stats")
    if (!metrics.isNullOrEmpty()) {
        pathParts.add(metrics.joinToString(","))
    }
    return restClient.get {
        path(*pathParts.toTypedArray())
        parameter("filter_path", filterPath?.joinToString(","))
        parameters(extraParameters)
    }.parse(NodesStatsResponse.serializer(), json)
}
