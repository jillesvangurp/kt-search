package com.jillesvangurp.ktsearch.cli.command.cluster

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.output.JsonOutputRenderer
import com.jillesvangurp.ktsearch.cli.output.OutputOptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ClusterCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "cluster") {
    override fun help(context: Context): String = "Cluster-level commands."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            ClusterHealthCommand(service),
            ClusterStatsCommand(service),
            ClusterStateCommand(service),
            ClusterSettingsCommand(service),
            ClusterPendingTasksCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private abstract class BaseClusterReadCommand(
    protected val service: CliService,
    name: String,
) : CoreSuspendingCliktCommand(name = name) {
    private val outputOptions by OutputOptions()

    protected open suspend fun fetch(
        connectionOptions: ConnectionOptions,
    ): String {
        return service.apiRequest(
            connectionOptions = connectionOptions,
            method = ApiMethod.Get,
            path = pathSegments(),
        )
    }

    protected abstract fun pathSegments(): List<String>

    protected fun requireConnectionOptions(): ConnectionOptions {
        return currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")
    }

    protected fun render(raw: String): String {
        return JsonOutputRenderer.renderTableOrRaw(
            rawJson = raw,
            outputFormat = outputOptions.outputFormat,
        )
    }
}

private class ClusterHealthCommand(
    service: CliService,
) : BaseClusterReadCommand(service = service, name = "health") {
    override fun help(context: Context): String =
        "Show cluster health from GET /_cluster/health."

    override fun pathSegments(): List<String> = listOf("_cluster", "health")

    override suspend fun fetch(connectionOptions: ConnectionOptions): String {
        return service.fetchClusterHealth(connectionOptions)
    }

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(render(raw))

        val json = runCatching {
            Json.Default.parseToJsonElement(raw).jsonObject
        }.getOrNull()
        val timedOut = (json?.get("timed_out") as? JsonPrimitive)?.content ==
            "true"
        val status = (json?.get("status") as? JsonPrimitive)
            ?.content
            ?.lowercase()

        if (timedOut || status == ClusterStatus.Red.name.lowercase()) {
            throw ProgramResult(2)
        }
    }
}

private class ClusterStatsCommand(
    service: CliService,
) : BaseClusterReadCommand(service = service, name = "stats") {
    override fun help(context: Context): String =
        "Show cluster stats from GET /_cluster/stats."

    override fun pathSegments(): List<String> = listOf("_cluster", "stats")

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(render(raw))
    }
}

private class ClusterStateCommand(
    service: CliService,
) : BaseClusterReadCommand(service = service, name = "state") {
    override fun help(context: Context): String =
        "Show cluster state from GET /_cluster/state."

    override fun pathSegments(): List<String> = listOf("_cluster", "state")

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(render(raw))
    }
}

private class ClusterSettingsCommand(
    service: CliService,
) : BaseClusterReadCommand(service = service, name = "settings") {
    override fun help(context: Context): String =
        "Show cluster settings from GET /_cluster/settings."

    private val includeDefaults by option(
        "--include-defaults",
        help = "Include default settings.",
    ).flag(default = false)

    private val flatSettings by option(
        "--flat-settings",
        help = "Return settings in flat key format.",
    ).flag(default = false)

    override fun pathSegments(): List<String> = listOf("_cluster", "settings")

    override suspend fun fetch(connectionOptions: ConnectionOptions): String {
        val parameters = mutableMapOf<String, String>()
        if (includeDefaults) {
            parameters["include_defaults"] = "true"
        }
        if (flatSettings) {
            parameters["flat_settings"] = "true"
        }
        return service.apiRequest(
            connectionOptions = connectionOptions,
            method = ApiMethod.Get,
            path = pathSegments(),
            parameters = parameters.takeIf { it.isNotEmpty() },
        )
    }

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(render(raw))
    }
}

private class ClusterPendingTasksCommand(
    service: CliService,
) : BaseClusterReadCommand(service = service, name = "pending-tasks") {
    override fun help(context: Context): String =
        "Show pending tasks from GET /_cluster/pending_tasks."

    override fun pathSegments(): List<String> = listOf("_cluster", "pending_tasks")

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(render(raw))
    }
}
