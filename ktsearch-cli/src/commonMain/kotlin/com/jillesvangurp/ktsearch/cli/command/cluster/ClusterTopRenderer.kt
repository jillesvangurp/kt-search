package com.jillesvangurp.ktsearch.cli.command.cluster

import com.jillesvangurp.ktsearch.ClusterStatus
import com.github.ajalt.mordant.rendering.TextColors
import kotlin.math.roundToInt

class ClusterTopRenderer(
    private val useColor: Boolean,
) {
    fun render(snapshot: ClusterTopSnapshot, intervalSeconds: Int): String {
        val width = terminalWidth()
        val metricWidth = ((width - 4) / 3).coerceAtLeast(18)
        val lines = mutableListOf<String>()

        val statusLabel = snapshot.status?.name?.lowercase() ?: "-"
        val statusColored = styleStatus(statusLabel, snapshot.status)
        val header = buildString {
            append("cluster ")
            append(snapshot.clusterName)
            append("  status=")
            append(statusColored)
            append("  nodes=")
            append(snapshot.nodeCount ?: snapshot.nodes.size)
            append("  interval=")
            append(intervalSeconds)
            append("s")
        }
        lines += truncate(header, width)
        lines += "shards a/r/i/u=" +
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
        lines += "updated=${snapshot.fetchedAt}  " +
            "idx/s=${colorizedRate(snapshot.indexRatePerSecond)}  " +
            "qry/s=${colorizedRate(snapshot.queryRatePerSecond)}"
        lines += ""
        lines += joinBars(
            left = metricBar("cpu", snapshot.cpuAvg, snapshot.cpuMax, metricWidth),
            middle = metricBar("heap", snapshot.heapAvg, snapshot.heapMax, metricWidth),
            right = metricBar("disk", snapshot.diskAvg, snapshot.diskMax, metricWidth),
            width = width,
        )
        lines += ""
        lines += truncate(
            "node         ip              roles      cpu heap disk docs    segs   " +
                "store    tp-q tp-r i/s  q/s",
            width,
        )
        lines += truncate("-".repeat(width), width)

        snapshot.nodes.forEach { node ->
            val line = formatNodeLine(node)
            lines += truncate(line, width)
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
        val avgText = colorizedPercent(avg)
        val maxText = colorizedPercent(max)
        return "$name avg=$avgText max=$maxText [$bar]"
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
            pad(colorizedPercent(node.cpuPercent), 3),
            pad(colorizedPercent(node.heapPercent), 4),
            pad(colorizedPercent(node.diskPercent), 4),
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

    private fun severity(value: Double): ColorLevel? {
        return when {
            value >= 90.0 -> ColorLevel.Critical
            value >= 75.0 -> ColorLevel.Warning
            else -> null
        }
    }

    private fun colorizedPercent(value: Double?): String {
        val percent = fmtPercent(value)
        val severity = value?.let { severity(it) }
        return colorizeLevel(percent, severity)
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
