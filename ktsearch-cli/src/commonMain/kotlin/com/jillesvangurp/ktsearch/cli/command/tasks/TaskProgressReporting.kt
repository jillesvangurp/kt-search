package com.jillesvangurp.ktsearch.cli.command.tasks

import kotlin.math.round
import kotlin.time.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parsed task progress counters from `_tasks/{id}` responses. */
data class TaskProgress(
    val taskId: String,
    val total: Long?,
    val processed: Long,
    val batches: Long? = null,
)

internal data class EtaEstimate(
    val docsPerSecond: Double?,
    val etaSeconds: Long?,
)

internal class TaskEtaTracker {
    private val startedAt = Clock.System.now()
    private var startProcessed: Long? = null

    fun update(progress: TaskProgress): EtaEstimate {
        if (startProcessed == null) {
            startProcessed = progress.processed
        }
        val elapsed = (Clock.System.now() - startedAt).inWholeSeconds
        val processedSinceStart = progress.processed -
            (startProcessed ?: progress.processed)
        if (elapsed <= 0 || processedSinceStart <= 0) {
            return EtaEstimate(docsPerSecond = null, etaSeconds = null)
        }
        val rate = processedSinceStart.toDouble() / elapsed.toDouble()
        val remaining = progress.total?.minus(progress.processed)
        val eta = if (remaining != null && remaining > 0 && rate > 0.0) {
            (remaining / rate).toLong()
        } else {
            null
        }
        return EtaEstimate(docsPerSecond = rate, etaSeconds = eta)
    }
}

internal fun taskProgress(obj: JsonObject, taskId: String): TaskProgress? {
    val status = obj["task"]?.jsonObject?.get("status")?.jsonObject ?: return null
    val created = status["created"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val updated = status["updated"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val deleted = status["deleted"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
    val explicitProcessed = status["processed"]?.jsonPrimitive
        ?.contentOrNull?.toLongOrNull()
    val completedCount = status["completed"]?.jsonPrimitive
        ?.contentOrNull?.toLongOrNull()

    val processed = when {
        created != null || updated != null || deleted != null ->
            (created ?: 0L) + (updated ?: 0L) + (deleted ?: 0L)
        explicitProcessed != null -> explicitProcessed
        completedCount != null -> completedCount
        else -> return null
    }
    return TaskProgress(
        taskId = taskId,
        total = status["total"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        processed = processed,
        batches = status["batches"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    )
}

internal fun formatEta(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        "${h}h${m}m${s}s"
    } else if (m > 0) {
        "${m}m${s}s"
    } else {
        "${s}s"
    }
}

internal fun printTaskProgressLine(
    prefix: String,
    progress: TaskProgress,
    previousLength: Int,
    eta: EtaEstimate,
): Int {
    val pct = progress.total?.takeIf { it > 0 }?.let { total ->
        (progress.processed * 100.0) / total
    }
    val line = buildString {
        append(prefix)
        append(" ")
        pct?.let { append("${oneDecimal(it)}% ") }
        append("${progress.processed}")
        progress.total?.let { append("/$it") }
        progress.batches?.let { append(" batches=$it") }
        eta.docsPerSecond?.let {
            append(" rate=${oneDecimal(it)}/s")
        }
        eta.etaSeconds?.takeIf { it >= 0 }?.let {
            append(" eta=${formatEta(it)}")
        }
    }
    val padded = if (previousLength > line.length) {
        line + " ".repeat(previousLength - line.length)
    } else {
        line
    }
    print("\r$padded")
    return maxOf(previousLength, line.length)
}

private fun oneDecimal(value: Double): String {
    val rounded = round(value * 10.0) / 10.0
    val intPart = rounded.toLong()
    val frac = round((rounded - intPart) * 10.0).toLong()
    return "$intPart.$frac"
}
