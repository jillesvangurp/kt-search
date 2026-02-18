package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson

class CreateIndexCommand(
    private val service: CliService,
    name: String = "create",
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Create an index."

    private val index by argument(help = "Index name.")
    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = false)

    override suspend fun run() {
        val body = readBody(data, file, required = false, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf(index),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

class GetIndexCommand(
    private val service: CliService,
    name: String = "get",
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Get index metadata."

    private val index by argument(help = "Index name.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf(index),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

class IndexExistsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "exists") {
    override fun help(context: Context): String = "Check if an index exists."

    private val index by argument(help = "Index name.")

    override suspend fun run() {
        val exists = service.indexExists(requireConnectionOptions(), index)
        echo("$exists")
    }
}

class DeleteIndexCommand(
    private val service: CliService,
    private val platform: CliPlatform,
    name: String = "delete",
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Delete an index."

    private val index by argument(help = "Index name.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val dryRun by option(
        "--dry-run",
        help = "Preview request without executing.",
    ).flag(default = false)
    private val ignoreUnavailable by option(
        "--ignore-unavailable",
        help = "Ignore missing indices (default true).",
    ).choice("true", "false").default("true")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete index '$index'?",
        )
        if (dryRun) {
            echo("dry-run: DELETE /$index?ignore_unavailable=$ignoreUnavailable")
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf(index),
            parameters = mapOf("ignore_unavailable" to ignoreUnavailable),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}
