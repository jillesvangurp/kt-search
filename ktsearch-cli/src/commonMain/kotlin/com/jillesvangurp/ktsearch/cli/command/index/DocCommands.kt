package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson

class DocCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "doc") {
    override fun help(context: Context): String = "Document CRUD and mget."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            DocGetCommand(service),
            DocIndexCommand(service),
            DocDeleteCommand(service, platform),
            DocMGetCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class DocGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get a document by id."

    private val index by argument(help = "Index name.")
    private val id by argument(help = "Document id.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf(index, "_doc", id),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class DocIndexCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "index") {
    override fun help(context: Context): String = "Index a document."

    private val index by argument(help = "Index name.")
    private val id by option("--id", help = "Document id. If omitted, auto id.")
    private val opType by option(
        "--op-type",
        help = "Operation type (default index).",
    ).choice("index", "create").default("index")
    private val data by option("-d", "--data", help = "Raw JSON document.")
    private val file by option("-f", "--file", help = "Read JSON document from file.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val path = if (id == null) {
            listOf(index, "_doc")
        } else {
            listOf(index, "_doc", id!!)
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = path,
            parameters = mapOf("op_type" to opType),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class DocDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "delete") {
    override fun help(context: Context): String = "Delete a document by id."

    private val index by argument(help = "Index name.")
    private val id by argument(help = "Document id.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete document '$id' from '$index'?",
        )
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf(index, "_doc", id),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class DocMGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "mget") {
    override fun help(context: Context): String = "Run _mget on an index."

    private val index by argument(help = "Index name.")
    private val data by option("-d", "--data", help = "Raw JSON mget payload.")
    private val file by option("-f", "--file", help = "Read JSON payload from file.")
    private val pretty by option("--pretty", help = "Pretty-print output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf(index, "_mget"),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}
