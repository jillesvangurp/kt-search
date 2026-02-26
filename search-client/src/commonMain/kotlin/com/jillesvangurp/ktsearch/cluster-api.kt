@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ClusterStatus {
    @SerialName("red")
    Red,

    @SerialName("yellow")
    Yellow,

    @SerialName("green")
    Green
}

val ClusterStatus.usable: Boolean get() = this == ClusterStatus.Green || this == ClusterStatus.Yellow

@Serializable
data class ClusterHealthResponse(
    @SerialName("cluster_name")
    val clusterName: String,
    val status: ClusterStatus,
    @SerialName("timed_out")
    val timedOut: Boolean,
    @SerialName("number_of_nodes")
    val numberOfNodes: Int? = null,
    @SerialName("active_shards")
    val activeShards: Int? = null,
    @SerialName("relocating_shards")
    val relocatingShards: Int? = null,
    @SerialName("initializing_shards")
    val initializingShards: Int? = null,
    @SerialName("unassigned_shards")
    val unassignedShards: Int? = null,
)

suspend fun SearchClient.clusterHealth(
    extraParameters: Map<String, String>? = null,
): ClusterHealthResponse {
    return restClient.get {
        path("_cluster", "health")
        parameters(extraParameters)
    }.parse(ClusterHealthResponse.serializer(), json)
}

/**
 * Summary response for `GET /_cluster/stats`.
 *
 * This model intentionally captures only the fields needed for operational
 * dashboards. All fields are nullable to stay compatible across
 * Elasticsearch and OpenSearch versions.
 */
@Serializable
data class ClusterStatsResponse(
    @SerialName("cluster_name")
    val clusterName: String? = null,
    val status: ClusterStatus? = null,
    val indices: ClusterStatsIndices? = null,
    val nodes: ClusterStatsNodes? = null,
)

@Serializable
data class ClusterStatsIndices(
    val docs: ClusterStatsDocs? = null,
    val shards: ClusterStatsShards? = null,
    val store: ClusterStatsStore? = null,
    val segments: ClusterStatsSegments? = null,
)

@Serializable
data class ClusterStatsDocs(
    val count: Long? = null,
)

@Serializable
data class ClusterStatsShards(
    val total: Int? = null,
)

@Serializable
data class ClusterStatsStore(
    @SerialName("size_in_bytes")
    val sizeInBytes: Long? = null,
)

@Serializable
data class ClusterStatsSegments(
    val count: Long? = null,
    @SerialName("memory_in_bytes")
    val memoryInBytes: Long? = null,
)

@Serializable
data class ClusterStatsNodes(
    val count: ClusterStatsNodeCount? = null,
    val fs: ClusterStatsFs? = null,
)

@Serializable
data class ClusterStatsNodeCount(
    val total: Int? = null,
    val data: Int? = null,
    val master: Int? = null,
    val ingest: Int? = null,
)

@Serializable
data class ClusterStatsFs(
    @SerialName("total_in_bytes")
    val totalInBytes: Long? = null,
    @SerialName("available_in_bytes")
    val availableInBytes: Long? = null,
)

/**
 * `GET /_cluster/stats`.
 *
 * Use [filterPath] to reduce payload size with `filter_path` and
 * [extraParameters] for additional query parameters.
 */
suspend fun SearchClient.clusterStats(
    filterPath: List<String>? = null,
    extraParameters: Map<String, String>? = null,
): ClusterStatsResponse {
    return restClient.get {
        path("_cluster", "stats")
        parameter("filter_path", filterPath?.joinToString(","))
        parameters(extraParameters)
    }.parse(ClusterStatsResponse.serializer(), json)
}
