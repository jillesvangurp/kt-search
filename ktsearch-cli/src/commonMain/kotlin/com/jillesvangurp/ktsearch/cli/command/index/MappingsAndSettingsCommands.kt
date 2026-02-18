package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson

class MappingsCommand(
    service: CliService,
) : CoreSuspendingCliktCommand(name = "mappings") {
    override fun help(context: Context): String = "Manage index mappings."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            GetMappingsCommand(service),
            PutMappingsCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class GetMappingsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get index mappings."

    private val index by argument(help = "Index name.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf(index, "_mappings"),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class PutMappingsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "put") {
    override fun help(context: Context): String = "Update index mappings."

    private val index by argument(help = "Index name.")
    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf(index, "_mapping"),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

class SettingsCommand(
    service: CliService,
) : CoreSuspendingCliktCommand(name = "settings") {
    override fun help(context: Context): String = "Manage index settings."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            GetSettingsCommand(service),
            PutSettingsCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class GetSettingsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get index settings."

    private val index by argument(help = "Index name.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf(index, "_settings"),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class PutSettingsCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "put") {
    override fun help(context: Context): String = "Update index settings."

    private val index by argument(help = "Index name.")
    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf(index, "_settings"),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}
