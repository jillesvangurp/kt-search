package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService

class IlmCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "ilm") {
    override fun help(context: Context): String = "Manage ILM policies."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            IlmPutCommand(service),
            IlmGetCommand(service),
            IlmDeleteCommand(service, platform),
            IlmStatusCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class IlmPutCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "put") {
    override fun help(context: Context): String = "Create or update ILM policy."

    private val policy by argument(help = "Policy id.")
    private val data by option("-d", "--data", help = "Raw policy JSON.")
    private val file by option("-f", "--file", help = "Read JSON from file.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf("_ilm", "policy", policy),
            data = body,
        )
        echoJson(response, pretty)
    }
}

private class IlmGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get ILM policy/policies."

    private val policy by argument(help = "Policy id.").optional()
    private val pretty by prettyFlag()

    override suspend fun run() {
        val path = buildList {
            add("_ilm")
            add("policy")
            policy?.let { add(it) }
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = path,
        )
        echoJson(response, pretty)
    }
}

private class IlmDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "delete") {
    override fun help(context: Context): String = "Delete ILM policy."

    private val policy by argument(help = "Policy id.")
    private val yes by yesFlag()
    private val pretty by prettyFlag()

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete ILM policy '$policy'?",
        )
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf("_ilm", "policy", policy),
        )
        echoJson(response, pretty)
    }
}

private class IlmStatusCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "status") {
    override fun help(context: Context): String = "Get ILM status."

    private val pretty by prettyFlag()

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = listOf("_ilm", "status"),
        )
        echoJson(response, pretty)
    }
}
