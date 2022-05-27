package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClusterHealthResponse(
    @SerialName("cluster_name")
    val clusterName: String,
    val status: ClusterStatus,
    @SerialName("timed_out")
    val timedOut: Boolean,
)

suspend fun SearchClient.clusterHealth(): ClusterHealthResponse {
    return restClient.get {
        path("_cluster", "health")
    }.parse(ClusterHealthResponse.serializer())
}