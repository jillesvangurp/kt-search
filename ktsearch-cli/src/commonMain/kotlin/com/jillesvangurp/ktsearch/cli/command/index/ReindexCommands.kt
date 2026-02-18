package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson
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
        help = "Wait for completion true|false (default true).",
    ).choice("true", "false").default("true")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .choice("true", "false").default("true")

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_reindex"),
            parameters = mapOf("wait_for_completion" to wait),
            data = body,
        )
        if (pretty == "true") {
            echo(prettyJson(response))
        } else {
            echo(response)
        }
    }
}

class ReindexTaskStatusCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "reindex-task-status") {
    override fun help(context: Context): String = "Get _tasks status for reindex task."

    private val taskId by argument(help = "Task id.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .choice("true", "false").default("true")

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf("_tasks", taskId),
        )
        if (pretty == "true") {
            echo(prettyJson(response))
        } else {
            echo(response)
        }
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
    ).int().default(2)
    private val timeoutSeconds by option(
        "--timeout-seconds",
        help = "Max wait seconds.",
    ).int().default(600)

    override suspend fun run() {
        val started = kotlin.time.Clock.System.now()
        while (true) {
            val response = service.apiRequest(
                connectionOptions = requireConnectionOptions(),
                method = ApiMethod.Get,
                path = listOf("_tasks", taskId),
            )
            val obj = Json.Default.decodeFromString(JsonObject.serializer(), response)
            val completed = obj["completed"]?.jsonPrimitive?.content == "true"
            if (completed) {
                echo(prettyJson(response))
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
