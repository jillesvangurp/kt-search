package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ReindexTaskProgress
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReindexCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "reindex") {
    override fun help(context: Context): String = "Run _reindex."

    private val data by option("-d", "--data", help = "Raw reindex JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val wait by option(
        "--wait",
        help = "Wait for completion true|false (default false).",
    ).choice("true", "false").default("false")
    private val disableRefreshInterval by option(
        "--disable-refresh-interval",
        help = "Temporarily set destination refresh_interval to -1.",
    ).flag(default = false)
    private val setReplicasToZero by option(
        "--set-replicas-zero",
        help = "Temporarily set destination number_of_replicas to 0.",
    ).flag(default = false)
    private val pretty by prettyFlag()

    override suspend fun run() {
        val body = requireNotNull(
            readBody(data, file, required = true, currentContext),
        )
        val tracker = ReindexEtaTracker()
        var progressLen = 0
        val response = service.reindex(
            connectionOptions = requireConnectionOptions(),
            body = body,
            waitForCompletion = wait == "true",
            disableRefreshInterval = disableRefreshInterval,
            setReplicasToZero = setReplicasToZero,
            onTaskProgress = { progress ->
                progressLen = printProgressLine(
                    progress = progress,
                    previousLength = progressLen,
                    eta = tracker.update(progress),
                )
            },
        )
        if (progressLen > 0) {
            println()
        }
        echoJson(response, pretty)
    }
}

class ReindexTaskStatusCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "reindex-task-status") {
    override fun help(context: Context): String = "Get _tasks status for reindex task."

    private val taskId by argument(help = "Task id.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf("_tasks", taskId),
        )
        echoJson(response, pretty)
    }
}

class ReindexWaitCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "reindex-wait") {
    override fun help(context: Context): String = "Poll reindex task until completion."

    private val taskId by argument(help = "Task id.")
    private val intervalSeconds by option(
        "--interval-seconds",
        help = "Polling interval seconds.",
    ).int().default(5)
    private val timeoutSeconds by option(
        "--timeout-seconds",
        help = "Max wait seconds.",
    ).int().default(600)

    override suspend fun run() {
        if (intervalSeconds < 2) {
            currentContext.fail("--interval-seconds must be >= 2")
        }
        val started = kotlin.time.Clock.System.now()
        val tracker = ReindexEtaTracker()
        var progressLen = 0
        while (true) {
            val response = service.apiRequest(
                connectionOptions = requireConnectionOptions(),
                method = ApiMethod.Get,
                path = listOf("_tasks", taskId),
            )
            val obj = Json.Default.decodeFromString(JsonObject.serializer(), response)
            reindexTaskProgress(obj, taskId)?.let {
                progressLen = printProgressLine(
                    progress = it,
                    previousLength = progressLen,
                    eta = tracker.update(it),
                )
            }
            val completed = obj["completed"]?.jsonPrimitive?.content == "true"
            if (completed) {
                if (progressLen > 0) {
                    println()
                }
                echoJson(response, pretty = true)
                return
            }
            val waited = kotlin.time.Clock.System.now() - started
            if (waited > timeoutSeconds.seconds) {
                currentContext.fail("Timed out waiting for task $taskId")
            }
            delay(intervalSeconds.seconds)
        }
    }
}

private fun reindexTaskProgress(obj: JsonObject, taskId: String): ReindexTaskProgress? {
    val status = obj["task"]?.jsonObject?.get("status")?.jsonObject ?: return null
    return ReindexTaskProgress(
        taskId = taskId,
        total = status["total"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
        created = status["created"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        updated = status["updated"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        deleted = status["deleted"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
        batches = status["batches"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
    )
}

private data class EtaEstimate(
    val docsPerSecond: Double?,
    val etaSeconds: Long?,
)

private class ReindexEtaTracker {
    private val startedAt = Clock.System.now()
    private var startProcessed: Long? = null

    fun update(progress: ReindexTaskProgress): EtaEstimate {
        if (startProcessed == null) {
            startProcessed = progress.processed
        }
        val elapsed = (Clock.System.now() - startedAt).inWholeSeconds
        val processedSinceStart = progress.processed - (startProcessed ?: progress.processed)
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

private fun formatEta(seconds: Long): String {
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

private fun printProgressLine(
    progress: ReindexTaskProgress,
    previousLength: Int,
    eta: EtaEstimate,
): Int {
    val pct = progress.total?.takeIf { it > 0 }?.let { total ->
        (progress.processed * 100.0) / total
    }
    val line = buildString {
        append("reindex ")
        pct?.let { append("${"%.1f".format(it)}% ") }
        append("${progress.processed}")
        progress.total?.let { append("/$it") }
        progress.batches?.let { append(" batches=$it") }
        eta.docsPerSecond?.let {
            append(" rate=${"%.1f".format(it)}/s")
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
