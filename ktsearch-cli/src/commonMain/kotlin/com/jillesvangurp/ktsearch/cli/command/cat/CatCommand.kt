package com.jillesvangurp.ktsearch.cli.command.cat

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.jillesvangurp.ktsearch.cli.CatRequest
import com.jillesvangurp.ktsearch.cli.CatVariant
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.output.JsonOutputRenderer
import com.jillesvangurp.ktsearch.cli.output.OutputOptions

class CatCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "cat") {
    override fun help(context: Context): String =
        "Run cat APIs and render the output as a table."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            CatTargetCommand(service, "aliases", CatVariant.Aliases, "Alias name."),
            CatTargetCommand(
                service,
                "allocation",
                CatVariant.Allocation,
                "Node id.",
            ),
            CatTargetCommand(service, "count", CatVariant.Count, "Index target."),
            CatNoArgCommand(service, "health", CatVariant.Health),
            CatTargetCommand(
                service,
                "indices",
                CatVariant.Indices,
                "Index target.",
            ),
            CatNoArgCommand(service, "master", CatVariant.Master),
            CatTargetCommand(service, "nodes", CatVariant.Nodes, "Node id."),
            CatNoArgCommand(service, "pending-tasks", CatVariant.PendingTasks),
            CatTargetCommand(
                service,
                "recovery",
                CatVariant.Recovery,
                "Index target.",
            ),
            CatNoArgCommand(service, "repositories", CatVariant.Repositories),
            CatTargetCommand(service, "shards", CatVariant.Shards, "Index target."),
            CatTargetCommand(
                service,
                "snapshots",
                CatVariant.Snapshots,
                "Repository (default: _all).",
            ),
            CatNoArgCommand(service, "tasks", CatVariant.Tasks),
            CatTargetCommand(service, "templates", CatVariant.Templates, "Name."),
            CatTargetCommand(
                service,
                "thread-pool",
                CatVariant.ThreadPool,
                "Pattern(s).",
            ),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private abstract class BaseCatSubCommand(
    private val service: CliService,
    name: String,
    private val variant: CatVariant,
) : CoreSuspendingCliktCommand(name = name) {
    private val queryOptions by CatQueryOptions()
    private val outputOptions by OutputOptions()

    abstract fun target(): String?

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")
        val request = CatRequest(
            variant = variant,
            target = target(),
            columns = queryOptions.columns,
            sort = queryOptions.sort,
            verbose = queryOptions.verbose,
            help = queryOptions.help,
            bytes = queryOptions.bytes,
            time = queryOptions.time,
            local = queryOptions.local,
        )
        val raw = service.cat(connectionOptions, request)
        val output = JsonOutputRenderer.renderTableOrRaw(
            rawJson = raw,
            outputFormat = outputOptions.outputFormat,
            preferredColumns = request.columns,
        )
        echo(output)
    }
}

private class CatNoArgCommand(
    service: CliService,
    name: String,
    variant: CatVariant,
) : BaseCatSubCommand(service = service, name = name, variant = variant) {
    override fun target(): String? = null
}

private class CatTargetCommand(
    service: CliService,
    name: String,
    variant: CatVariant,
    argumentHelp: String,
) : BaseCatSubCommand(service = service, name = name, variant = variant) {
    private val value by argument(help = argumentHelp).optional()

    override fun target(): String? = value
}

private class CatQueryOptions : OptionGroup(name = "Cat Query") {
    private val columnsRaw by option(
        "--columns",
        help = "Comma-separated columns (h).",
    )

    private val sortRaw by option(
        "--sort",
        help = "Comma-separated sort columns (s).",
    )

    val verbose by option(
        "--verbose",
        help = "Show header row from server (v).",
    ).flag(default = false)

    val help by option(
        "--help-columns",
        help = "Return available columns (help).",
    ).flag(default = false)

    val bytes by option(
        "--bytes",
        help = "Byte unit.",
    ).choice("b", "kb", "mb", "gb", "tb", "pb")

    val time by option(
        "--time",
        help = "Time unit.",
    ).choice("d", "h", "m", "s", "ms", "micros", "nanos")

    private val localRaw by option(
        "--local",
        help = "Read from local node only (true|false).",
    ).choice("true", "false")

    val columns: List<String>?
        get() = parseList(columnsRaw)

    val sort: List<String>?
        get() = parseList(sortRaw)

    val local: Boolean?
        get() = when (localRaw) {
            "true" -> true
            "false" -> false
            else -> null
        }
}

private fun parseList(value: String?): List<String>? {
    return value
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?.takeIf { it.isNotEmpty() }
}
