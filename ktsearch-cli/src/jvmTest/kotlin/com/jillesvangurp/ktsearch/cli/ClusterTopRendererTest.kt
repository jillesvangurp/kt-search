package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopRenderer
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopSnapshot
import com.jillesvangurp.ktsearch.cli.command.cluster.NodeTopSnapshot
import com.jillesvangurp.ktsearch.cli.command.cluster.fmtBytes
import com.jillesvangurp.ktsearch.cli.command.cluster.fmtCount
import com.jillesvangurp.ktsearch.cli.command.cluster.truncate
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Clock

class ClusterTopRendererTest {
    @Test
    fun truncatesLongLines() {
        truncate("abcdefghij", 7) shouldBe "abcd..."
    }

    @Test
    fun formatsCountsAndBytesCompactly() {
        fmtCount(1_500) shouldBe "1.5k"
        fmtBytes(1_048_576) shouldBe "1.0mb"
    }

    @Test
    fun rendersMissingMetricsAsDash() {
        val snapshot = ClusterTopSnapshot(
            fetchedAt = Clock.System.now(),
            clusterName = "demo",
            status = null,
            activeShards = null,
            relocatingShards = null,
            initializingShards = null,
            unassignedShards = null,
            clusterDocs = null,
            clusterShards = null,
            clusterStoreBytes = null,
            clusterSegments = null,
            clusterSegmentMemoryBytes = null,
            nodeCount = 1,
            cpuAvg = null,
            cpuMax = null,
            heapAvg = null,
            heapMax = null,
            diskAvg = null,
            diskMax = null,
            indexRatePerSecond = null,
            queryRatePerSecond = null,
            nodes = listOf(
                NodeTopSnapshot(
                    id = "node-1",
                    name = "node-1",
                    ip = "127.0.0.1",
                    roles = "",
                    cpuPercent = null,
                    heapPercent = null,
                    diskPercent = null,
                    docs = null,
                    shards = null,
                    segments = null,
                    storeBytes = null,
                    threadPoolQueue = null,
                    threadPoolRejected = null,
                    indexRatePerSecond = null,
                    queryRatePerSecond = null,
                ),
            ),
            errors = listOf("nodes stats unavailable"),
        )
        val rendered = ClusterTopRenderer(useColor = false).render(
            snapshot = snapshot,
            intervalSeconds = 3,
        )

        rendered shouldContain "cpu avg=-"
        rendered shouldContain "node-1"
        rendered shouldContain "warn: nodes stats unavailable"
    }
}
