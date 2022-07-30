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
)

suspend fun SearchClient.clusterHealth(
    extraParameters: Map<String, String>? = null,
): ClusterHealthResponse {
    return restClient.get {
        path("_cluster", "health")
        parameters(extraParameters)
    }.parse(ClusterHealthResponse.serializer(), json)
}

