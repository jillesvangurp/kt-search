package com.jillesvangurp.ktsearch.cli.command.tasks

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.prettyJson
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class TasksCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "tasks") {
    override fun help(context: Context): String = "Task API commands."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            TaskStatusCommand(service),
            TaskWaitCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class TaskStatusCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "status") {
    override fun help(context: Context): String = "Get _tasks status for task."

    private val taskId by argument(help = "Task id.")

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf("_tasks", taskId),
        )
        echoJson(response, pretty = true)
    }
}

private class TaskWaitCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "wait") {
    override fun help(context: Context): String = "Poll task until completion."

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
                    prefix = "task",
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

private fun CoreSuspendingCliktCommand.requireConnectionOptions() =
    currentContext.findObject<ConnectionOptions>()
        ?: error("Missing connection options in command context")

private fun CoreSuspendingCliktCommand.echoJson(
    response: String,
    pretty: Boolean,
) {
    echo(if (pretty) prettyJson(response) else response)
}
