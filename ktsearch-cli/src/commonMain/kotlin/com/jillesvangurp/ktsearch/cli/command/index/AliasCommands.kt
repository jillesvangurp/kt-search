package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService

class AliasCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "alias") {
    override fun help(context: Context): String =
        "Manage aliases with atomic _aliases operations."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            AliasGetCommand(service),
            AliasUpdateCommand(service),
            AliasAddCommand(service),
            AliasRemoveCommand(service),
            AliasRemoveIndexCommand(service, platform),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class AliasGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get aliases for target or all."

    private val target by argument(help = "Index or alias target.").optional()
    private val pretty by prettyFlag()

    override suspend fun run() {
        val path = buildList {
            target?.let { add(it) }
            add("_alias")
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = path,
        )
        echoJson(response, pretty)
    }
}

private class AliasUpdateCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "update") {
    override fun help(context: Context): String = "Apply atomic alias actions from JSON."

    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val dryRun by dryRunFlag("Print request body without executing.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        if (dryRun) {
            echo(body)
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_aliases"),
            data = body,
        )
        echoJson(response, pretty)
    }
}

private class AliasAddCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "add") {
    override fun help(context: Context): String = "Atomically add alias to index."

    private val index by argument(help = "Index name.")
    private val alias by argument(help = "Alias name.")
    private val write by option(
        "--write",
        help = "Set is_write_index true|false.",
    ).choice("true", "false")
    private val dryRun by dryRunFlag("Print request body without executing.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        val writePart = when (write) {
            "true" -> """, "is_write_index": true"""
            "false" -> """, "is_write_index": false"""
            else -> ""
        }
        val body = """
            {"actions":[{"add":{"index":"$index","alias":"$alias"$writePart}}]}
        """.trimIndent()
        if (dryRun) {
            echo(body)
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_aliases"),
            data = body,
        )
        echoJson(response, pretty)
    }
}

private class AliasRemoveCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "remove") {
    override fun help(context: Context): String = "Atomically remove alias from index."

    private val index by argument(help = "Index name.")
    private val alias by argument(help = "Alias name.")
    private val dryRun by dryRunFlag("Print request body without executing.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        val body = """
            {"actions":[{"remove":{"index":"$index","alias":"$alias"}}]}
        """.trimIndent()
        if (dryRun) {
            echo(body)
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_aliases"),
            data = body,
        )
        echoJson(response, pretty)
    }
}

private class AliasRemoveIndexCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "remove-index") {
    override fun help(context: Context): String =
        "Atomically remove index via alias API (deletes index)."

    private val index by argument(help = "Index name.")
    private val yes by yesFlag()
    private val dryRun by dryRunFlag("Print request body without executing.")
    private val pretty by prettyFlag()

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete index '$index' via alias remove_index?",
        )
        val body = """
            {"actions":[{"remove_index":{"index":"$index"}}]}
        """.trimIndent()
        if (dryRun) {
            echo(body)
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Post,
            path = listOf("_aliases"),
            data = body,
        )
        echoJson(response, pretty)
    }
}
