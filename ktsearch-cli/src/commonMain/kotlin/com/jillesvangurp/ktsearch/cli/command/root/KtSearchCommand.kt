package com.jillesvangurp.ktsearch.cli.command.root

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.DefaultCliPlatform
import com.jillesvangurp.ktsearch.cli.DefaultCliService
import com.jillesvangurp.ktsearch.cli.command.cat.CatCommand
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterCommand
import com.jillesvangurp.ktsearch.cli.command.info.InfoCommand
import com.jillesvangurp.ktsearch.cli.command.index.IndexCommand

/** Root command and shared options for the CLI. */
class KtSearchCommand(
    private val service: CliService = DefaultCliService(),
    private val platform: CliPlatform = DefaultCliPlatform,
) : CoreSuspendingCliktCommand(name = "ktsearch") {
    override fun help(context: Context): String =
        "Swiss-army CLI for Elasticsearch/OpenSearch operations."

    override val invokeWithoutSubcommand: Boolean = true

    private val host by option(
        "--host",
        help = "Search host.",
        envvar = "KTSEARCH_HOST",
    ).default("localhost")

    private val port by option(
        "--port",
        help = "Search port.",
        envvar = "KTSEARCH_PORT",
    ).int().default(9200)

    private val https by option(
        "--https",
        help = "Use HTTPS.",
    ).flag(default = false)

    private val http by option(
        "--http",
        help = "Force HTTP.",
    ).flag(default = false)

    private val httpsFromEnv by option(
        "--https-from-env",
        hidden = true,
        envvar = "KTSEARCH_HTTPS",
    )

    private val user by option(
        "--user",
        help = "Basic auth username.",
        envvar = "KTSEARCH_USER",
    )

    private val password by option(
        "--password",
        help = "Basic auth password.",
        envvar = "KTSEARCH_PASSWORD",
    )

    private val loggingFromEnv by option(
        "--logging-from-env",
        hidden = true,
        envvar = "KTSEARCH_LOGGING",
    )

    private val logging by option(
        "--logging",
        help = "Enable client request logging.",
    ).flag(default = false)

    init {
        subcommands(
            ClusterCommand(service),
            InfoCommand(service),
            CatCommand(service),
            IndexCommand(service, platform),
            SuspendingCompletionCommand(name = "completion"),
        )
    }

    override suspend fun run() {
        currentContext.obj = ConnectionOptions(
            host = host,
            port = port,
            https = when {
                http -> false
                https -> true
                else -> parseBoolean(httpsFromEnv)
            },
            user = user,
            password = password,
            logging = if (logging) true else parseBoolean(loggingFromEnv),
        )

        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

private fun parseBoolean(value: String?): Boolean {
    return when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "y", "on" -> true
        else -> false
    }
}
