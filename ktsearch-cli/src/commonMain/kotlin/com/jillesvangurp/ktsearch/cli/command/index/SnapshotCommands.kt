package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson

class SnapshotCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "snapshot") {
    override fun help(context: Context): String = "Manage snapshot repos/snapshots."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            SnapshotRepoCommand(service, platform),
            SnapshotCreateCommand(service),
            SnapshotListCommand(service),
            SnapshotDeleteCommand(service, platform),
            SnapshotRestoreCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class SnapshotRepoCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "repo") {
    override fun help(context: Context): String = "Manage snapshot repositories."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            SnapshotRepoCreateCommand(service),
            SnapshotRepoGetCommand(service),
            SnapshotRepoDeleteCommand(service, platform),
            SnapshotRepoVerifyCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class SnapshotRepoCreateCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "create") {
    override fun help(context: Context): String = "Create or update a snapshot repo."

    private val repository by argument(help = "Repository name.")
    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf("_snapshot", repository),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotRepoGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get snapshot repo(s)."

    private val repository by argument(help = "Repository name.").optional()
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val path = buildList {
            add("_snapshot")
            repository?.let { add(it) }
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = path,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotRepoDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "delete") {
    override fun help(context: Context): String = "Delete a snapshot repository."

    private val repository by argument(help = "Repository name.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete snapshot repository '$repository'?",
        )
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf("_snapshot", repository),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotRepoVerifyCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "verify") {
    override fun help(context: Context): String = "Verify a snapshot repository."

    private val repository by argument(help = "Repository name.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_snapshot", repository, "_verify"),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotCreateCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "create") {
    override fun help(context: Context): String = "Create a snapshot."

    private val repository by argument(help = "Repository name.")
    private val snapshot by argument(help = "Snapshot name.")
    private val data by option("-d", "--data", help = "Optional JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = false, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf("_snapshot", repository, snapshot),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotListCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "list") {
    override fun help(context: Context): String = "List snapshots in a repository."

    private val repository by argument(help = "Repository name.")
    private val pattern by argument(help = "Snapshot pattern.").default("_all")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf("_snapshot", repository, pattern),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "delete") {
    override fun help(context: Context): String = "Delete a snapshot."

    private val repository by argument(help = "Repository name.")
    private val snapshot by argument(help = "Snapshot name.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete snapshot '$repository/$snapshot'?",
        )
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf("_snapshot", repository, snapshot),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class SnapshotRestoreCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "restore") {
    override fun help(context: Context): String = "Restore a snapshot."

    private val repository by argument(help = "Repository name.")
    private val snapshot by argument(help = "Snapshot name.")
    private val data by option("-d", "--data", help = "Optional restore JSON.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = false, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_snapshot", repository, snapshot, "_restore"),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}
