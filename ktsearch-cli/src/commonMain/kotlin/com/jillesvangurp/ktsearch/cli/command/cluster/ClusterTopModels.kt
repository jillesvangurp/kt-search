package com.jillesvangurp.ktsearch.cli.command.cluster

import com.jillesvangurp.ktsearch.ClusterStatsResponse
import com.jillesvangurp.ktsearch.ClusterHealthResponse
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.NodesStatsResponse
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ClusterTopApiSnapshot(
    val clusterStats: ClusterStatsResponse?,
    val clusterHealth: ClusterHealthResponse?,
    val nodesStats: NodesStatsResponse?,
    val indicesStatsRaw: String? = null,
    val threadPoolCatRaw: String? = null,
    val allocationCatRaw: String? = null,
    val clusterSettingsRaw: String? = null,
    val tasksRaw: String? = null,
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
    val offendingIndices: List<IndexOffenderSnapshot>,
    val imbalance: NodeImbalanceSnapshot?,
    val threadPoolSaturation: List<ThreadPoolSaturationSnapshot>,
    val watermarks: WatermarkSnapshot?,
    val slowTasks: List<SlowTaskSnapshot>,
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

data class IndexOffenderSnapshot(
    val index: String,
    val docs: Long?,
    val storeBytes: Long?,
    val segmentMemoryBytes: Long?,
    val indexingRatePerSecond: Double?,
    val queryRatePerSecond: Double?,
)

data class NodeImbalanceSnapshot(
    val maxDiskPercent: Double?,
    val minDiskPercent: Double?,
    val maxShards: Int?,
    val minShards: Int?,
    val worstDiskNode: String?,
    val mostShardsNode: String?,
)

data class ThreadPoolSaturationSnapshot(
    val node: String,
    val pool: String,
    val active: Int?,
    val queue: Int?,
    val rejected: Long?,
)

data class WatermarkSnapshot(
    val low: String?,
    val high: String?,
    val floodStage: String?,
    val maxDiskPercent: Double?,
)

data class SlowTaskSnapshot(
    val node: String,
    val action: String,
    val runningTime: Duration?,
    val description: String?,
)

internal fun ClusterTopApiSnapshot.toTopSnapshot(
    previous: ClusterTopApiSnapshot?,
): ClusterTopSnapshot {
    val nodeStats = nodesStats?.nodes.orEmpty()
    val previousNodes = previous?.nodesStats?.nodes.orEmpty()
    val elapsed = previous?.let { fetchedAt - it.fetchedAt }
    val indicesStats = indicesStatsRaw?.parseObjectOrNull()
    val previousIndicesStats = previous?.indicesStatsRaw?.parseObjectOrNull()

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
        offendingIndices = parseOffendingIndices(
            current = indicesStats,
            previous = previousIndicesStats,
            elapsed = elapsed,
        ),
        imbalance = parseImbalance(allocationCatRaw),
        threadPoolSaturation = parseThreadPoolSaturation(threadPoolCatRaw),
        watermarks = parseWatermarks(
            clusterSettingsRaw = clusterSettingsRaw,
            allocationCatRaw = allocationCatRaw,
        ),
        slowTasks = parseSlowTasks(tasksRaw),
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

private fun parseOffendingIndices(
    current: JsonObject?,
    previous: JsonObject?,
    elapsed: Duration?,
): List<IndexOffenderSnapshot> {
    val currentIndices = current?.get("indices")?.jsonObject ?: return emptyList()
    val previousIndices = previous?.get("indices")?.jsonObject.orEmpty()
    return currentIndices.entries.map { (index, value) ->
        val total = value.jsonObject["total"]?.jsonObject
        val docs = total?.get("docs")?.jsonObject?.get("count").longOrNull()
        val store = total?.get("store")?.jsonObject?.get("size_in_bytes").longOrNull()
        val segMem = total?.get("segments")?.jsonObject
            ?.get("memory_in_bytes").longOrNull()
        val idxTotal = total?.get("indexing")?.jsonObject
            ?.get("index_total").longOrNull()
        val qryTotal = total?.get("search")?.jsonObject
            ?.get("query_total").longOrNull()
        val prevTotal = previousIndices[index]?.jsonObject?.get("total")?.jsonObject
        val idxRate = deltaRate(
            idxTotal,
            prevTotal?.get("indexing")?.jsonObject?.get("index_total").longOrNull(),
            elapsed,
        )
        val qryRate = deltaRate(
            qryTotal,
            prevTotal?.get("search")?.jsonObject?.get("query_total").longOrNull(),
            elapsed,
        )
        IndexOffenderSnapshot(
            index = index,
            docs = docs,
            storeBytes = store,
            segmentMemoryBytes = segMem,
            indexingRatePerSecond = idxRate,
            queryRatePerSecond = qryRate,
        )
    }.sortedByDescending { offenderScore(it) }.take(8)
}

private fun offenderScore(value: IndexOffenderSnapshot): Double {
    return (value.storeBytes ?: 0L) / (1024.0 * 1024.0 * 1024.0) +
        (value.segmentMemoryBytes ?: 0L) / (1024.0 * 1024.0) +
        (value.indexingRatePerSecond ?: 0.0) / 100.0 +
        (value.queryRatePerSecond ?: 0.0) / 100.0
}

private fun parseThreadPoolSaturation(
    raw: String?,
): List<ThreadPoolSaturationSnapshot> {
    val array = raw?.parseArrayOrNull() ?: return emptyList()
    return array.mapNotNull { item ->
        val obj = item.jsonObject
        ThreadPoolSaturationSnapshot(
            node = obj["node_name"].stringOrNull() ?: return@mapNotNull null,
            pool = obj["name"].stringOrNull() ?: return@mapNotNull null,
            active = obj["active"].intOrNull(),
            queue = obj["queue"].intOrNull(),
            rejected = obj["rejected"].longOrNull(),
        )
    }.sortedWith(
        compareByDescending<ThreadPoolSaturationSnapshot> { it.queue ?: 0 }
            .thenByDescending { it.rejected ?: 0L },
    ).take(8)
}

private fun parseImbalance(raw: String?): NodeImbalanceSnapshot? {
    val array = raw?.parseArrayOrNull() ?: return null
    val rows = array.mapNotNull { item ->
        val obj = item.jsonObject
        val node = obj["node"].stringOrNull() ?: return@mapNotNull null
        AllocationRow(
            node = node,
            diskPercent = obj["disk.percent"].doubleOrNull(),
            shards = obj["shards"].intOrNull(),
        )
    }
    if (rows.isEmpty()) {
        return null
    }
    val maxDisk = rows.maxByOrNull { it.diskPercent ?: Double.MIN_VALUE }
    val minDisk = rows.minByOrNull { it.diskPercent ?: Double.MAX_VALUE }
    val maxShards = rows.maxByOrNull { it.shards ?: Int.MIN_VALUE }
    val minShards = rows.minByOrNull { it.shards ?: Int.MAX_VALUE }
    return NodeImbalanceSnapshot(
        maxDiskPercent = maxDisk?.diskPercent,
        minDiskPercent = minDisk?.diskPercent,
        maxShards = maxShards?.shards,
        minShards = minShards?.shards,
        worstDiskNode = maxDisk?.node,
        mostShardsNode = maxShards?.node,
    )
}

private data class AllocationRow(
    val node: String,
    val diskPercent: Double?,
    val shards: Int?,
)

private fun parseWatermarks(
    clusterSettingsRaw: String?,
    allocationCatRaw: String?,
): WatermarkSnapshot? {
    val settings = clusterSettingsRaw?.parseObjectOrNull()
    val persistent = settings?.get("persistent")?.jsonObject.orEmpty()
    val transient = settings?.get("transient")?.jsonObject.orEmpty()
    val defaults = settings?.get("defaults")?.jsonObject.orEmpty()
    fun value(key: String): String? {
        return transient[key].stringOrNull()
            ?: persistent[key].stringOrNull()
            ?: defaults[key].stringOrNull()
    }
    val maxDiskPercent = allocationCatRaw?.parseArrayOrNull()
        ?.mapNotNull { it.jsonObject["disk.percent"].doubleOrNull() }
        ?.maxOrNull()
    return WatermarkSnapshot(
        low = value("cluster.routing.allocation.disk.watermark.low"),
        high = value("cluster.routing.allocation.disk.watermark.high"),
        floodStage = value("cluster.routing.allocation.disk.watermark.flood_stage"),
        maxDiskPercent = maxDiskPercent,
    )
}

private fun parseSlowTasks(raw: String?): List<SlowTaskSnapshot> {
    val root = raw?.parseObjectOrNull() ?: return emptyList()
    val nodes = root["nodes"]?.jsonObject ?: return emptyList()
    return nodes.values.flatMap { node ->
        val nodeObj = node.jsonObject
        val nodeName = nodeObj["name"].stringOrNull()
            ?: nodeObj["host"].stringOrNull()
            ?: "node"
        val tasks = nodeObj["tasks"]?.jsonObject.orEmpty()
        tasks.values.mapNotNull { task ->
            val taskObj = task.jsonObject
            val runningNanos = taskObj["running_time_in_nanos"].longOrNull()
            val runningTime = runningNanos?.nanoseconds
            SlowTaskSnapshot(
                node = nodeName,
                action = taskObj["action"].stringOrNull() ?: return@mapNotNull null,
                runningTime = runningTime,
                description = taskObj["description"].stringOrNull(),
            )
        }
    }.sortedByDescending { it.runningTime ?: Duration.ZERO }.take(8)
}

private fun String.parseObjectOrNull(): JsonObject? {
    return runCatching {
        Json.Default.parseToJsonElement(this).jsonObject
    }.getOrNull()
}

private fun String.parseArrayOrNull() = runCatching {
    Json.Default.parseToJsonElement(this).jsonArray
}.getOrNull()

private fun JsonElement?.stringOrNull(): String? =
    (this as? JsonPrimitive)?.contentOrNull()

private fun JsonElement?.longOrNull(): Long? = stringOrNull()?.toLongOrNull()

private fun JsonElement?.intOrNull(): Int? = stringOrNull()?.toIntOrNull()

private fun JsonElement?.doubleOrNull(): Double? = stringOrNull()?.toDoubleOrNull()

private fun JsonPrimitive.contentOrNull(): String? {
    return if (isString) content else content
}
