package com.jillesvangurp.ktsearch.cli.command.cloud

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.output.JsonOutputRenderer
import com.jillesvangurp.ktsearch.cli.output.OutputOptions
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class CloudCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "cloud") {
    override fun help(context: Context): String = "Cloud helper commands."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(ElasticCloudCommand(service))
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class ElasticCloudCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "elastic") {
    override fun help(context: Context): String = "Elastic Cloud helpers."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            ElasticCloudContextCommand(),
            ElasticCloudCheckCommand(service),
            ElasticCloudStatusCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class ElasticCloudContextCommand :
    CoreSuspendingCliktCommand(name = "context") {
    override fun help(context: Context): String =
        "Show effective Elastic Cloud endpoint and auth context."

    override suspend fun run() {
        val connection = requireConnectionOptions()
        val json = buildJsonObject {
            put("host", JsonPrimitive(connection.host))
            put("port", JsonPrimitive(connection.port))
            put("https", JsonPrimitive(connection.https))
            if (!connection.cloudId.isNullOrBlank()) {
                put("cloud_id", JsonPrimitive(connection.cloudId))
            }
            put("auth_mode", JsonPrimitive(authMode(connection)))
        }
        echo(json.toString())
    }
}

private abstract class BaseElasticCloudReadCommand(
    private val service: CliService,
    name: String,
) : CoreSuspendingCliktCommand(name = name) {
    private val outputOptions by OutputOptions()

    protected abstract suspend fun fetch(connectionOptions: ConnectionOptions): String

    override suspend fun run() {
        val raw = fetch(requireConnectionOptions())
        echo(
            JsonOutputRenderer.renderTableOrRaw(
                rawJson = raw,
                outputFormat = outputOptions.outputFormat,
            ),
        )
    }

    protected suspend fun fetchRootInfo(connectionOptions: ConnectionOptions): String {
        return service.fetchRootInfo(connectionOptions)
    }

    protected suspend fun fetchClusterHealth(
        connectionOptions: ConnectionOptions,
    ): String {
        return service.fetchClusterHealth(connectionOptions)
    }
}

private class ElasticCloudCheckCommand(
    service: CliService,
) : BaseElasticCloudReadCommand(service = service, name = "check") {
    override fun help(context: Context): String =
        "Check Elastic Cloud endpoint by calling GET /."

    override suspend fun fetch(connectionOptions: ConnectionOptions): String {
        return fetchRootInfo(connectionOptions)
    }
}

private class ElasticCloudStatusCommand(
    service: CliService,
) : BaseElasticCloudReadCommand(service = service, name = "status") {
    override fun help(context: Context): String =
        "Show Elastic Cloud cluster health."

    override suspend fun fetch(connectionOptions: ConnectionOptions): String {
        return fetchClusterHealth(connectionOptions)
    }
}

private fun CoreSuspendingCliktCommand.requireConnectionOptions():
    ConnectionOptions {
    return currentContext.findObject<ConnectionOptions>()
        ?: error("Missing connection options in command context")
}

private fun authMode(connectionOptions: ConnectionOptions): String {
    return when {
        connectionOptions.awsSigV4 -> "aws-sigv4"
        !connectionOptions.elasticApiKey.isNullOrBlank() -> "api-key"
        !connectionOptions.user.isNullOrBlank() -> "basic"
        else -> "none"
    }
}
