package com.jillesvangurp.ktsearch.cli.command.cluster

import com.jillesvangurp.ktsearch.ClusterStatsResponse
import com.jillesvangurp.ktsearch.ClusterHealthResponse
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.NodesStatsResponse
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.Instant

data class ClusterTopApiSnapshot(
    val clusterStats: ClusterStatsResponse?,
    val clusterHealth: ClusterHealthResponse?,
    val nodesStats: NodesStatsResponse?,
    val errors: List<String> = emptyList(),
    val fetchedAt: Instant,
)

data class ClusterTopSnapshot(
    val fetchedAt: Instant,
    val clusterName: String,
    val status: ClusterStatus?,
    val activeShards: Int?,
    val relocatingShards: Int?,
    val initializingShards: Int?,
    val unassignedShards: Int?,
    val clusterDocs: Long?,
    val clusterShards: Int?,
    val clusterStoreBytes: Long?,
    val clusterSegments: Long?,
    val clusterSegmentMemoryBytes: Long?,
    val nodeCount: Int?,
    val cpuAvg: Double?,
    val cpuMax: Double?,
    val heapAvg: Double?,
    val heapMax: Double?,
    val diskAvg: Double?,
    val diskMax: Double?,
    val indexRatePerSecond: Double?,
    val queryRatePerSecond: Double?,
    val nodes: List<NodeTopSnapshot>,
    val errors: List<String> = emptyList(),
)

data class NodeTopSnapshot(
    val id: String,
    val name: String,
    val ip: String,
    val roles: String,
    val cpuPercent: Double?,
    val heapPercent: Double?,
    val diskPercent: Double?,
    val docs: Long?,
    val shards: Int?,
    val segments: Long?,
    val storeBytes: Long?,
    val threadPoolQueue: Long?,
    val threadPoolRejected: Long?,
    val indexRatePerSecond: Double?,
    val queryRatePerSecond: Double?,
)

internal fun ClusterTopApiSnapshot.toTopSnapshot(
    previous: ClusterTopApiSnapshot?,
): ClusterTopSnapshot {
    val nodeStats = nodesStats?.nodes.orEmpty()
    val previousNodes = previous?.nodesStats?.nodes.orEmpty()
    val elapsed = previous?.let { fetchedAt - it.fetchedAt }

    val nodes = nodeStats.map { (id, node) ->
        val totalDisk = node.fs?.total?.totalInBytes
        val availableDisk = node.fs?.total?.availableInBytes
        val usedDisk = if (totalDisk != null && availableDisk != null) {
            totalDisk - availableDisk
        } else {
            null
        }
        val diskPercent = ratioPercent(usedDisk, totalDisk)
        val heapPercent = node.jvm?.mem?.heapUsedPercent?.toDouble()
            ?: ratioPercent(
                node.jvm?.mem?.heapUsedInBytes,
                node.jvm?.mem?.heapMaxInBytes,
            )

        val pools = node.threadPool.orEmpty().values
        val queue = pools.sumOf { it.queue ?: 0L }
            .takeIf { pools.isNotEmpty() }
        val rejected = pools.sumOf { it.rejected ?: 0L }
            .takeIf { pools.isNotEmpty() }

        val prev = previousNodes[id]
        val indexRate = deltaRate(
            current = node.indices?.indexing?.indexTotal,
            previous = prev?.indices?.indexing?.indexTotal,
            elapsed = elapsed,
        )
        val queryRate = deltaRate(
            current = node.indices?.search?.queryTotal,
            previous = prev?.indices?.search?.queryTotal,
            elapsed = elapsed,
        )
        NodeTopSnapshot(
            id = id,
            name = node.name ?: id,
            ip = node.ip ?: node.host ?: "-",
            roles = node.roles.orEmpty().sorted().joinToString(","),
            cpuPercent = node.os?.cpu?.percent?.toDouble()
                ?: node.process?.cpu?.percent?.toDouble(),
            heapPercent = heapPercent,
            diskPercent = diskPercent,
            docs = node.indices?.docs?.count,
            shards = null,
            segments = node.indices?.segments?.count,
            storeBytes = node.indices?.store?.sizeInBytes,
            threadPoolQueue = queue,
            threadPoolRejected = rejected,
            indexRatePerSecond = indexRate,
            queryRatePerSecond = queryRate,
        )
    }.sortedBy { it.name }

    return ClusterTopSnapshot(
        fetchedAt = fetchedAt,
        clusterName = clusterHealth?.clusterName
            ?: clusterStats?.clusterName
            ?: nodesStats?.clusterName
            ?: "unknown",
        status = clusterHealth?.status ?: clusterStats?.status,
        activeShards = clusterHealth?.activeShards,
        relocatingShards = clusterHealth?.relocatingShards,
        initializingShards = clusterHealth?.initializingShards,
        unassignedShards = clusterHealth?.unassignedShards,
        clusterDocs = clusterStats?.indices?.docs?.count,
        clusterShards = clusterStats?.indices?.shards?.total,
        clusterStoreBytes = clusterStats?.indices?.store?.sizeInBytes,
        clusterSegments = clusterStats?.indices?.segments?.count,
        clusterSegmentMemoryBytes = clusterStats?.indices?.segments?.memoryInBytes,
        nodeCount = clusterStats?.nodes?.count?.total ?: nodes.size,
        cpuAvg = average(nodes.mapNotNull { it.cpuPercent }),
        cpuMax = max(nodes.mapNotNull { it.cpuPercent }),
        heapAvg = average(nodes.mapNotNull { it.heapPercent }),
        heapMax = max(nodes.mapNotNull { it.heapPercent }),
        diskAvg = average(nodes.mapNotNull { it.diskPercent }),
        diskMax = max(nodes.mapNotNull { it.diskPercent }),
        indexRatePerSecond = sumRates(nodes.mapNotNull { it.indexRatePerSecond }),
        queryRatePerSecond = sumRates(nodes.mapNotNull { it.queryRatePerSecond }),
        nodes = nodes,
        errors = errors,
    )
}

private fun ratioPercent(used: Long?, total: Long?): Double? {
    if (used == null || total == null || total <= 0L) {
        return null
    }
    return (used.toDouble() / total.toDouble()) * 100.0
}

private fun deltaRate(
    current: Long?,
    previous: Long?,
    elapsed: Duration?,
): Double? {
    if (current == null || previous == null || elapsed == null) {
        return null
    }
    val elapsedSeconds = elapsed.toDouble(DurationUnit.SECONDS)
    if (elapsedSeconds <= 0.0) {
        return null
    }
    val delta = current - previous
    if (delta < 0L) {
        return null
    }
    return delta.toDouble() / elapsedSeconds
}

private fun average(values: List<Double>): Double? {
    if (values.isEmpty()) {
        return null
    }
    return values.sum() / values.size.toDouble()
}

private fun max(values: List<Double>): Double? = values.maxOrNull()

private fun sumRates(values: List<Double>): Double? {
    if (values.isEmpty()) {
        return null
    }
    return values.sum()
}
