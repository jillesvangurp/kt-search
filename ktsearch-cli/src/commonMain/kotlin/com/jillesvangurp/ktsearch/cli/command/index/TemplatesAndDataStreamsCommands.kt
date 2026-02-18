package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.ApiMethod
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.prettyJson

class TemplateCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "template") {
    override fun help(context: Context): String = "Manage component/index templates."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            ComponentTemplateCommand(service, platform),
            IndexTemplateCommand(service, platform),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class ComponentTemplateCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "component") {
    override fun help(context: Context): String = "Manage component templates."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            TemplateGetCommand(service, "get", "_component_template"),
            TemplatePutCommand(service, "put", "_component_template"),
            TemplateDeleteCommand(
                service = service,
                platform = platform,
                name = "delete",
                endpoint = "_component_template",
                subject = "component template",
            ),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class IndexTemplateCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "index") {
    override fun help(context: Context): String = "Manage index templates."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            TemplateGetCommand(service, "get", "_index_template"),
            TemplatePutCommand(service, "put", "_index_template"),
            TemplateDeleteCommand(
                service = service,
                platform = platform,
                name = "delete",
                endpoint = "_index_template",
                subject = "index template",
            ),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class TemplateGetCommand(
    private val service: CliService,
    name: String,
    private val endpoint: String,
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Get template by id or all."

    private val templateId by argument(help = "Template id.").optional()
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val path = buildList {
            add(endpoint)
            templateId?.let { add(it) }
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = path,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class TemplatePutCommand(
    private val service: CliService,
    name: String,
    private val endpoint: String,
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Create or update template."

    private val templateId by argument(help = "Template id.")
    private val data by option("-d", "--data", help = "Raw JSON body.")
    private val file by option("-f", "--file", help = "Read JSON body from file.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val body = readBody(data, file, required = true, currentContext)
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf(endpoint, templateId),
            data = body,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class TemplateDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
    name: String,
    private val endpoint: String,
    private val subject: String,
) : CoreSuspendingCliktCommand(name = name) {
    override fun help(context: Context): String = "Delete a template."

    private val templateId by argument(help = "Template id.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val dryRun by option(
        "--dry-run",
        help = "Preview request without executing.",
    ).flag(default = false)
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete $subject '$templateId'?",
        )
        if (dryRun) {
            echo("dry-run: DELETE /$endpoint/$templateId")
            return
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf(endpoint, templateId),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

class DataStreamCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "data-stream") {
    override fun help(context: Context): String = "Manage data streams."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            DataStreamCreateCommand(service),
            DataStreamGetCommand(service),
            DataStreamDeleteCommand(service, platform),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private class DataStreamCreateCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "create") {
    override fun help(context: Context): String = "Create a data stream."

    private val nameArg by argument(help = "Data stream name.")
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Put,
            path = listOf("_data_stream", nameArg),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class DataStreamGetCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "get") {
    override fun help(context: Context): String = "Get data stream info."

    private val nameArg by argument(help = "Data stream name.").optional()
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        val path = buildList {
            add("_data_stream")
            nameArg?.let { add(it) }
        }
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Get,
            path = path,
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}

private class DataStreamDeleteCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "delete") {
    override fun help(context: Context): String = "Delete a data stream."

    private val nameArg by argument(help = "Data stream name.")
    private val yes by option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)
    private val pretty by option("--pretty", help = "Pretty-print JSON output.")
        .flag(default = true)

    override suspend fun run() {
        requireConfirmation(
            context = currentContext,
            platform = platform,
            yes = yes,
            prompt = "Delete data stream '$nameArg'?",
        )
        val response = service.apiRequest(
            connectionOptions = requireConnectionOptions(),
            method = ApiMethod.Delete,
            path = listOf("_data_stream", nameArg),
        )
        echo(if (pretty) prettyJson(response) else response)
    }
}
