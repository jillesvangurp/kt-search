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
import com.jillesvangurp.ktsearch.cli.platformReadUtf8File
import com.jillesvangurp.ktsearch.cli.prettyJson
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class ApplyCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "apply") {
    override fun help(context: Context): String =
        "Apply JSON as mappings/settings/component-template/index-template."

    private val target by argument(help = "Index or template id.")
    private val file by option("-f", "--file", help = "Input JSON file.")
    private val data by option("-d", "--data", help = "Raw JSON payload.")
    private val kind by option("--kind", help = "Kind or auto.")
        .choice(
            "auto",
            "mappings",
            "settings",
            "component-template",
            "index-template",
        ).default("auto")

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)!!
        val selectedKind = if (kind == "auto") detectKind(body) else kind
        val (method, path) = when (selectedKind) {
            "mappings" -> ApiMethod.Put to listOf(target, "_mapping")
            "settings" -> ApiMethod.Put to listOf(target, "_settings")
            "component-template" -> {
                ApiMethod.Put to listOf("_component_template", target)
            }
            "index-template" -> ApiMethod.Put to listOf("_index_template", target)
            else -> currentContext.fail("Unsupported kind: $selectedKind")
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = method,
            path = path,
            data = body,
        )
        echo(prettyJson(response))
    }
}

class WaitGreenCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "wait-green") {
    override fun help(context: Context): String = "Wait until index health is green."

    private val index by argument(help = "Index name.")
    private val intervalSeconds by option("--interval-seconds")
        .int().default(2)
    private val timeoutSeconds by option("--timeout-seconds")
        .int().default(300)

    override suspend fun run() {
        val started = Clock.System.now()
        while (true) {
            val response = service.apiRequest(
                connectionOptions = requireConnectionOptions(),
                method = ApiMethod.Get,
                path = listOf("_cluster", "health", index),
            )
            val json = Json.Default.decodeFromString(JsonObject.serializer(), response)
            val status = json["status"]?.toString()?.trim('"')
            if (status == "green") {
                echo(prettyJson(response))
                return
            }
            if (Clock.System.now() - started > timeoutSeconds.seconds) {
                currentContext.fail("Timed out waiting for green status on $index")
            }
            delay(intervalSeconds.seconds)
        }
    }
}

class WaitExistsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "wait-exists") {
    override fun help(context: Context): String = "Wait until index exists."

    private val index by argument(help = "Index name.")
    private val intervalSeconds by option("--interval-seconds")
        .int().default(2)
    private val timeoutSeconds by option("--timeout-seconds")
        .int().default(300)

    override suspend fun run() {
        val started = Clock.System.now()
        while (true) {
            if (service.indexExists(requireConnectionOptions(), index)) {
                echo("true")
                return
            }
            if (Clock.System.now() - started > timeoutSeconds.seconds) {
                currentContext.fail("Timed out waiting for index $index")
            }
            delay(intervalSeconds.seconds)
        }
    }
}

private fun detectKind(json: String): String {
    val obj = Json.Default.decodeFromString(JsonObject.serializer(), json).jsonObject
    return when {
        obj.containsKey("index_patterns") -> "index-template"
        obj.containsKey("composed_of") -> "index-template"
        obj.containsKey("template") &&
            (obj.containsKey("priority") || obj.containsKey("data_stream")) -> {
            "index-template"
        }
        obj.containsKey("template") -> "component-template"
        obj.containsKey("mappings") -> "mappings"
        obj.containsKey("settings") -> "settings"
        else -> "mappings"
    }
}
