@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Granularity returned by the index statistics API. */
enum class IndexStatsLevel(internal val parameter: String) {
    Cluster("cluster"), Indices("indices"), Shards("shards"),
}

/** Index identities and shard statistics; unrelated engine fields are ignored. */
@Serializable
data class IndexStatsResponse(
    @SerialName("_shards") val shards: Shards? = null,
    val indices: Map<String, IndexStatsDetails> = emptyMap(),
)

/** Statistics for one concrete index, retaining its engine UUID. */
@Serializable
data class IndexStatsDetails(
    val uuid: String? = null,
    val shards: Map<String, List<IndexShardStats>> = emptyMap(),
)

/** One primary or replica copy of a shard. */
@Serializable
data class IndexShardStats(
    val routing: IndexShardRouting? = null,
    @SerialName("seq_no") val seqNo: IndexShardSequenceNumbers? = null,
)

/** Whether this shard copy is primary and its allocation state. */
@Serializable
data class IndexShardRouting(
    val primary: Boolean? = null,
    val state: String? = null,
)

/** Sequence numbers reported by the engine; empty shards may report -1. */
@Serializable
data class IndexShardSequenceNumbers(
    @SerialName("max_seq_no") val maxSeqNo: Long? = null,
    @SerialName("local_checkpoint") val localCheckpoint: Long? = null,
    @SerialName("global_checkpoint") val globalCheckpoint: Long? = null,
)

/**
 * Gets statistics for [target], or all indexes when omitted. Use [level] Shards
 * to inspect primary-shard sequence numbers and detect source changes during a
 * migration. Missing fields remain null rather than implying zero or success.
 */
suspend fun SearchClient.indexStats(
    target: String? = null,
    level: IndexStatsLevel? = null,
): IndexStatsResponse = restClient.get {
    path(*listOfNotNull(target?.takeIf { it.isNotBlank() }, "_stats").toTypedArray())
    parameter("level", level?.parameter)
}.parse(IndexStatsResponse.serializer(), json)
