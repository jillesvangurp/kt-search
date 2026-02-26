package com.jillesvangurp.ktsearch.cli.command.cluster

import com.jillesvangurp.ktsearch.ClusterStatus
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ClusterTopRenderer(
    private val useColor: Boolean,
) {
    fun renderHelp(): String {
        val lines = listOf(
            bold("top help"),
            "",
            "${bold("keys")}:",
            "  h/?   show this help",
            "  esc   close help",
            "  q     quit top",
            "",
            "${bold("header")}:",
            "  status: cluster health (green/yellow/red)",
            "  nodes:  node count, colored by cluster status",
            "  shards a/r/i/u: active/relocating/initializing/unassigned",
            "",
            "${bold("metrics")}:",
            "  cpu%   green<70, yellow<90, red>=90",
            "  heap%  green<80, yellow<90, red>=90",
            "  disk%  green<75, yellow<90, red>=90",
            "",
            "${bold("admin panels")}:",
            "  largest indices: highest resource/rate pressure indices",
            "  imbalance: node disk/shard skew summary",
            "  threadpool: queue/rejected pressure hotspots",
            "  watermarks: configured low/high/flood + current max disk%",
            "  slow tasks: long-running task watcher",
            "",
            "${bold("notes")}:",
            "  docs/segs/store are per-node totals.",
            "  tp-r is thread-pool rejected ops.",
            "  idx/s and qry/s are derived rates between refreshes.",
        )
        return lines.joinToString("\n")
    }

    fun render(snapshot: ClusterTopSnapshot, intervalSeconds: Int): String {
        val width = terminalWidth()
        val metricWidth = ((width - 4) / 3).coerceAtLeast(18)
        val lines = mutableListOf<String>()

        val statusLabel = snapshot.status?.name?.lowercase() ?: "-"
        val statusColored = styleStatus(statusLabel, snapshot.status)
        val header = buildString {
            append(bold("cluster"))
            append(" ")
            append(snapshot.clusterName)
            append("  ")
            append(bold("status"))
            append("=")
            append(statusColored)
            append("  ")
            append(bold("nodes"))
            append("=")
            append(
                styleByStatus(
                    (snapshot.nodeCount ?: snapshot.nodes.size).toString(),
                    snapshot.status,
                ),
            )
            append("  ")
            append(bold("interval"))
            append("=")
            append(intervalSeconds)
            append("s")
        }
        lines += truncate(header, width)
        lines += "${bold("shards")} a/r/i/u=" +
            "${fmtCount(snapshot.activeShards?.toLong())}/" +
            "${fmtCount(snapshot.relocatingShards?.toLong())}/" +
            "${fmtCount(snapshot.initializingShards?.toLong())}/" +
            colorizeLevel(
                fmtCount(snapshot.unassignedShards?.toLong()),
                if ((snapshot.unassignedShards ?: 0) > 0) {
                    ColorLevel.Critical
                } else {
                    ColorLevel.Good
                },
            )
        lines += "${bold("updated")}=${secondsAgo(snapshot.fetchedAt)}  " +
            "${bold("idx/s")}=${colorizedRate(snapshot.indexRatePerSecond)}  " +
            "${bold("qry/s")}=${colorizedRate(snapshot.queryRatePerSecond)}"
        lines += ""
        lines += joinBars(
            left = metricBar("cpu", snapshot.cpuAvg, snapshot.cpuMax, metricWidth),
            middle = metricBar("heap", snapshot.heapAvg, snapshot.heapMax, metricWidth),
            right = metricBar("disk", snapshot.diskAvg, snapshot.diskMax, metricWidth),
            width = width,
        )
        lines += ""
        lines += bold(
            truncate(
            "node         ip              roles      cpu heap disk docs    segs   " +
                "store    tp-q tp-r i/s  q/s",
            width,
            ),
        )
        lines += truncate("-".repeat(width), width)

        snapshot.nodes.forEach { node ->
            val line = formatNodeLine(node)
            lines += truncate(line, width)
        }

        if (snapshot.offendingIndices.isNotEmpty()) {
            lines += ""
            lines += bold("largest indices")
            lines += bold(
                truncate(
                    "index                          docs     store   " +
                        "segmem  i/s   q/s",
                    width,
                ),
            )
            snapshot.offendingIndices.take(6).forEach { idx ->
                lines += truncate(
                    "${pad(idx.index, 30)} " +
                        "${pad(fmtCount(idx.docs), 8)} " +
                        "${pad(fmtBytes(idx.storeBytes), 7)} " +
                        "${pad(fmtBytes(idx.segmentMemoryBytes), 7)} " +
                        "${pad(colorizedRate(idx.indexingRatePerSecond), 5)} " +
                        "${pad(colorizedRate(idx.queryRatePerSecond), 5)}",
                    width,
                )
            }
        }

        if (snapshot.imbalance != null) {
            lines += ""
            lines += bold("node imbalance")
            val imbalance = snapshot.imbalance
            val diskSpread = if (
                imbalance.maxDiskPercent != null && imbalance.minDiskPercent != null
            ) {
                imbalance.maxDiskPercent - imbalance.minDiskPercent
            } else {
                null
            }
            val shardsSpread = if (
                imbalance.maxShards != null && imbalance.minShards != null
            ) {
                imbalance.maxShards - imbalance.minShards
            } else {
                null
            }
            lines += "disk spread=${colorizedMetric("disk", diskSpread)}% " +
                "worst=${imbalance.worstDiskNode ?: "-"} " +
                "shard spread=${fmtCount(shardsSpread?.toLong())} " +
                "most=${imbalance.mostShardsNode ?: "-"}"
        }

        if (snapshot.threadPoolSaturation.isNotEmpty()) {
            lines += ""
            lines += bold("threadpool saturation")
            lines += bold(
                truncate(
                    "node                 pool             active queue " +
                        "rejected",
                    width,
                ),
            )
            snapshot.threadPoolSaturation.take(6).forEach { tp ->
                lines += truncate(
                    "${pad(tp.node, 20)} ${pad(tp.pool, 16)} " +
                        "${pad(fmtCount(tp.active?.toLong()), 6)} " +
                        "${pad(colorizedQueue(tp.queue), 5)} " +
                        "${pad(colorizedRejected(tp.rejected), 8)}",
                    width,
                )
            }
        }

        if (snapshot.watermarks != null) {
            lines += ""
            lines += bold("watermarks and thresholds")
            val wm = snapshot.watermarks
            lines += "low=${wm.low ?: "-"} high=${wm.high ?: "-"} " +
                "flood=${wm.floodStage ?: "-"} " +
                "maxDisk=${colorizedMetric("disk", wm.maxDiskPercent)}%"
        }

        if (snapshot.slowTasks.isNotEmpty()) {
            lines += ""
            lines += bold("slow task watcher")
            lines += bold(
                truncate(
                    "node                 sec   action                          " +
                        "desc",
                    width,
                ),
            )
            snapshot.slowTasks.take(6).forEach { task ->
                lines += truncate(
                    "${pad(task.node, 20)} " +
                        "${pad(colorizedTaskSeconds(task.runningTime), 5)} " +
                        "${pad(task.action, 30)} " +
                        "${truncate(task.description ?: "-", 40)}",
                    width,
                )
            }
        }

        if (snapshot.errors.isNotEmpty()) {
            lines += ""
            snapshot.errors.forEach { err ->
                lines += colorizeLevel("warn: $err", ColorLevel.Critical)
            }
        }

        return lines.joinToString("\n")
    }

    private fun terminalWidth(): Int {
        return 120
    }

    private fun metricBar(
        name: String,
        avg: Double?,
        max: Double?,
        width: Int,
    ): String {
        val value = avg ?: max
        if (value == null) {
            return "$name avg=- max=- [${"-".repeat((width - 16).coerceAtLeast(8))}]"
        }
        val barWidth = (width - 20).coerceAtLeast(8)
        val clamped = value.coerceIn(0.0, 100.0)
        val fill = ((clamped / 100.0) * barWidth.toDouble()).roundToInt()
        val bar = "#".repeat(fill) + "-".repeat((barWidth - fill).coerceAtLeast(0))
        val avgText = colorizedMetric(name, avg)
        val maxText = colorizedMetric(name, max)
        return "$name avg=$avgText max=$maxText [$bar]"
    }

    private fun secondsAgo(fetchedAt: Instant): String {
        val elapsed = Clock.System.now() - fetchedAt
        val seconds = elapsed.inWholeSeconds.coerceAtLeast(0)
        return "${seconds}s ago"
    }

    private fun joinBars(
        left: String,
        middle: String,
        right: String,
        width: Int,
    ): String {
        return truncate("$left  $middle  $right", width)
    }

    private fun formatNodeLine(node: NodeTopSnapshot): String {
        return listOf(
            pad(node.name, 12),
            pad(node.ip, 15),
            pad(shortRoles(node.roles), 10),
            pad(colorizedMetric("cpu", node.cpuPercent), 3),
            pad(colorizedMetric("heap", node.heapPercent), 4),
            pad(colorizedMetric("disk", node.diskPercent), 4),
            pad(fmtCount(node.docs), 7),
            pad(fmtCount(node.segments), 6),
            pad(fmtBytes(node.storeBytes), 8),
            pad(fmtCount(node.threadPoolQueue), 4),
            pad(colorizeLevel(
                fmtCount(node.threadPoolRejected),
                if ((node.threadPoolRejected ?: 0L) > 0L) {
                    ColorLevel.Critical
                } else {
                    null
                },
            ), 4),
            pad(colorizedRate(node.indexRatePerSecond), 4),
            pad(colorizedRate(node.queryRatePerSecond), 4),
        ).joinToString(" ")
    }

    private fun shortRoles(roles: String): String {
        if (roles.isBlank()) {
            return "-"
        }
        val mapped = roles.split(',').map { role ->
            when (role.trim()) {
                "master" -> "m"
                "data" -> "d"
                "ingest" -> "i"
                "remote_cluster_client" -> "r"
                "ml" -> "l"
                "transform" -> "t"
                else -> role.trim().take(1)
            }
        }
        return mapped.joinToString("")
    }

    private fun colorizeLevel(text: String, level: ColorLevel?): String {
        if (!useColor) {
            return text
        }
        return when (level) {
            ColorLevel.Good -> TextColors.green(text)
            ColorLevel.Warning -> TextColors.yellow(text)
            ColorLevel.Critical -> TextColors.red(text)
            null -> text
        }
    }

    private fun styleStatus(text: String, status: ClusterStatus?): String {
        return when (status) {
            ClusterStatus.Green -> colorizeLevel(text, ColorLevel.Good)
            ClusterStatus.Yellow -> colorizeLevel(text, ColorLevel.Warning)
            ClusterStatus.Red -> colorizeLevel(text, ColorLevel.Critical)
            null -> text
        }
    }

    private fun styleByStatus(text: String, status: ClusterStatus?): String {
        return when (status) {
            ClusterStatus.Green -> colorizeLevel(text, ColorLevel.Good)
            ClusterStatus.Yellow -> colorizeLevel(text, ColorLevel.Warning)
            ClusterStatus.Red -> colorizeLevel(text, ColorLevel.Critical)
            null -> text
        }
    }

    private fun severity(metric: String, value: Double): ColorLevel? {
        return when (metric) {
            "heap" -> when {
                value < 80.0 -> ColorLevel.Good
                value < 90.0 -> ColorLevel.Warning
                else -> ColorLevel.Critical
            }
            "cpu" -> when {
                value < 70.0 -> ColorLevel.Good
                value < 90.0 -> ColorLevel.Warning
                else -> ColorLevel.Critical
            }
            "disk" -> when {
                value < 75.0 -> ColorLevel.Good
                value < 90.0 -> ColorLevel.Warning
                else -> ColorLevel.Critical
            }
            else -> when {
                value < 70.0 -> ColorLevel.Good
                value < 90.0 -> ColorLevel.Warning
                else -> ColorLevel.Critical
            }
        }
    }

    private fun colorizedMetric(metric: String, value: Double?): String {
        val percent = fmtPercent(value)
        val level = value?.let { severity(metric, it) }
        return colorizeLevel(percent, level)
    }

    private fun bold(text: String): String {
        if (!useColor) {
            return text
        }
        return TextStyles.bold(text)
    }

    private fun severity(value: Double): ColorLevel? {
        return when {
            value >= 90.0 -> ColorLevel.Critical
            value >= 75.0 -> ColorLevel.Warning
            else -> null
        }
    }

    private fun colorizedRate(value: Double?): String {
        if (value == null) {
            return "-"
        }
        val level = when {
            value < 0.0 -> ColorLevel.Critical
            value > 0.0 -> ColorLevel.Good
            else -> null
        }
        return colorizeLevel(fmtRate(value), level)
    }

    private fun colorizedQueue(queue: Int?): String {
        val level = when {
            queue == null -> null
            queue >= 100 -> ColorLevel.Critical
            queue >= 20 -> ColorLevel.Warning
            queue > 0 -> ColorLevel.Good
            else -> null
        }
        return colorizeLevel(fmtCount(queue?.toLong()), level)
    }

    private fun colorizedRejected(rejected: Long?): String {
        val level = when {
            rejected == null -> null
            rejected > 0L -> ColorLevel.Critical
            else -> null
        }
        return colorizeLevel(fmtCount(rejected), level)
    }

    private fun colorizedTaskSeconds(runningTime: Duration?): String {
        val level = when {
            runningTime == null -> null
            runningTime >= 300.seconds -> ColorLevel.Critical
            runningTime >= 120.seconds -> ColorLevel.Warning
            else -> ColorLevel.Good
        }
        return colorizeLevel(fmtCount(runningTime?.inWholeSeconds), level)
    }
}

private enum class ColorLevel {
    Good,
    Warning,
    Critical,
}

internal fun fmtPercent(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return value.roundToInt().toString()
}

internal fun fmtCount(value: Long?): String {
    if (value == null) {
        return "-"
    }
    return when {
        value >= 1_000_000_000L -> "${(value / 1_000_000_000.0).round1()}g"
        value >= 1_000_000L -> "${(value / 1_000_000.0).round1()}m"
        value >= 1_000L -> "${(value / 1_000.0).round1()}k"
        else -> value.toString()
    }
}

internal fun fmtRate(value: Double?): String {
    if (value == null) {
        return "-"
    }
    return value.round1().toString()
}

internal fun fmtBytes(value: Long?): String {
    if (value == null) {
        return "-"
    }
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val tb = gb * 1024.0
    val doubleValue = value.toDouble()
    return when {
        doubleValue >= tb -> "${(doubleValue / tb).round1()}tb"
        doubleValue >= gb -> "${(doubleValue / gb).round1()}gb"
        doubleValue >= mb -> "${(doubleValue / mb).round1()}mb"
        doubleValue >= kb -> "${(doubleValue / kb).round1()}kb"
        else -> "${value}b"
    }
}

private fun Double.round1(): Double = (this * 10.0).roundToInt() / 10.0

internal fun truncate(value: String, width: Int): String {
    if (width <= 3 || value.length <= width) {
        return value
    }
    return value.take(width - 3) + "..."
}

private fun pad(value: String, width: Int): String {
    return truncate(value, width).padEnd(width, ' ')
}

internal fun ansiClear(): String = "\u001B[H\u001B[2J"
