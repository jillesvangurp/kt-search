package com.jillesvangurp.ktsearch.cli.command.root

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
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

    private val elasticApiKey by option(
        "--elastic-api-key",
        help = "Elastic API key.",
        envvar = "KTSEARCH_ELASTIC_API_KEY",
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

    private val awsSigV4 by option(
        "--aws-sigv4",
        help = "Enable AWS SigV4 request signing.",
    ).flag(default = false)

    private val awsSigV4FromEnv by option(
        "--aws-sigv4-from-env",
        hidden = true,
        envvar = "KTSEARCH_AWS_SIGV4",
    )

    private val awsRegion by option(
        "--aws-region",
        help = "AWS region for SigV4 (e.g. us-west-2).",
        envvar = "KTSEARCH_AWS_REGION",
    )

    private val awsRegionFromEnv by option(
        "--aws-region-from-env",
        hidden = true,
        envvar = "AWS_REGION",
    )

    private val awsDefaultRegionFromEnv by option(
        "--aws-default-region-from-env",
        hidden = true,
        envvar = "AWS_DEFAULT_REGION",
    )

    private val awsService by option(
        "--aws-service",
        help = "AWS SigV4 service name (defaults: aoss/es by host).",
        envvar = "KTSEARCH_AWS_SERVICE",
    )

    private val awsProfile by option(
        "--aws-profile",
        help = "AWS shared credentials profile name.",
        envvar = "KTSEARCH_AWS_PROFILE",
    )

    private val awsProfileFromEnv by option(
        "--aws-profile-from-env",
        hidden = true,
        envvar = "AWS_PROFILE",
    )

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
        val useAwsSigV4 = if (awsSigV4) true else parseBoolean(awsSigV4FromEnv)
        if (useAwsSigV4 && (!user.isNullOrBlank() || !password.isNullOrBlank())) {
            throw UsageError("--aws-sigv4 cannot be combined with --user/--password")
        }
        if (useAwsSigV4 && !elasticApiKey.isNullOrBlank()) {
            throw UsageError("--aws-sigv4 cannot be combined with --elastic-api-key")
        }

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
            elasticApiKey = elasticApiKey,
            logging = if (logging) true else parseBoolean(loggingFromEnv),
            awsSigV4 = useAwsSigV4,
            awsRegion = awsRegion ?: awsRegionFromEnv ?: awsDefaultRegionFromEnv,
            awsService = awsService,
            awsProfile = awsProfile ?: awsProfileFromEnv,
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
