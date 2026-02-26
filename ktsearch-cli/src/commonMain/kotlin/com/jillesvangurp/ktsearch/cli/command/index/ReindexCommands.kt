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
import com.jillesvangurp.ktsearch.cli.command.tasks.TaskEtaTracker
import com.jillesvangurp.ktsearch.cli.command.tasks.printTaskProgressLine
import com.jillesvangurp.ktsearch.cli.command.tasks.taskProgress
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val progressReporting by option(
        "--progress-reporting",
        help = "Poll task and print progress when --wait=false.",
    ).flag(default = false)
    private val pretty by prettyFlag()

    override suspend fun run() {
        val body = requireNotNull(
            readBody(data, file, required = true, currentContext),
        )
        val shouldReportProgress = progressReporting ||
            disableRefreshInterval ||
            setReplicasToZero
        val tracker = TaskEtaTracker()
        var progressLen = 0
        val response = service.reindex(
            connectionOptions = requireConnectionOptions(),
            body = body,
            waitForCompletion = wait == "true",
            disableRefreshInterval = disableRefreshInterval,
            setReplicasToZero = setReplicasToZero,
            onTaskProgress = if (shouldReportProgress) {
                { progress ->
                    progressLen = printTaskProgressLine(
                        prefix = "reindex",
                        progress = progress,
                        previousLength = progressLen,
                        eta = tracker.update(progress),
                    )
                }
            } else {
                null
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
        val tracker = TaskEtaTracker()
        var progressLen = 0
        while (true) {
            val response = service.apiRequest(
                connectionOptions = requireConnectionOptions(),
                method = ApiMethod.Get,
                path = listOf("_tasks", taskId),
            )
            val obj = Json.Default.decodeFromString(JsonObject.serializer(), response)
            taskProgress(obj, taskId)?.let {
                progressLen = printTaskProgressLine(
                    prefix = "reindex",
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
