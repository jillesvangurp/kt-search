package com.jillesvangurp.ktsearch.cli.command.root

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.command.cloud.CloudCommand
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.DefaultCliPlatform
import com.jillesvangurp.ktsearch.cli.DefaultCliService
import com.jillesvangurp.ktsearch.cli.platformGetEnv
import com.jillesvangurp.ktsearch.cli.command.cat.CatCommand
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterCommand
import com.jillesvangurp.ktsearch.cli.command.info.InfoCommand
import com.jillesvangurp.ktsearch.cli.command.index.IndexCommand
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.jillesvangurp.ktsearch.cli.command.tasks.TasksCommand
import com.jillesvangurp.ktsearch.cli.command.top.TopCommand

/** Root command and shared options for the CLI. */
class KtSearchCommand(
    private val service: CliService = DefaultCliService(),
    private val platform: CliPlatform = DefaultCliPlatform,
    private val envProvider: (String) -> String? = ::platformGetEnv,
) : CoreSuspendingCliktCommand(name = "ktsearch") {
    override fun help(context: Context): String =
        "Swiss-army CLI for Elasticsearch/OpenSearch operations."

    override val invokeWithoutSubcommand: Boolean = true

    private val host by option(
        "--host",
        help = "Search host.",
        envvar = "KTSEARCH_HOST",
    )

    private val port by option(
        "--port",
        help = "Search port.",
        envvar = "KTSEARCH_PORT",
    ).int()

    private val cloudId by option(
        "--cloud-id",
        help = "Elastic Cloud ID (overrides host/port, enforces HTTPS).",
        envvar = "KTSEARCH_CLOUD_ID",
    )

    private val cloudIdFromEnv by option(
        "--cloud-id-from-env",
        hidden = true,
        envvar = "ELASTIC_CLOUD_ID",
    )

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

    private val apiKey by option(
        "--api-key",
        help = "Elastic API key (alias for --elastic-api-key).",
        envvar = "ELASTIC_API_KEY",
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
        context {
            readEnvvar = envProvider
        }
        subcommands(
            ClusterCommand(service),
            InfoCommand(service),
            CatCommand(service),
            CloudCommand(service),
            IndexCommand(service, platform),
            TasksCommand(service),
            TopCommand(service),
            SuspendingCompletionCommand(name = "completion"),
        )
    }

    override suspend fun run() {
        val selectedCloudId = cloudId ?: cloudIdFromEnv
        val cloudEndpoint = selectedCloudId
            ?.takeIf { it.isNotBlank() }
            ?.let { id ->
                parseElasticCloudId(id) ?: throw UsageError(
                    "Invalid --cloud-id format. Expected Elastic Cloud ID.",
                )
            }
        if (cloudEndpoint != null && host != null) {
            throw UsageError("--cloud-id cannot be combined with --host")
        }
        if (cloudEndpoint != null && port != null) {
            throw UsageError("--cloud-id cannot be combined with --port")
        }
        if (cloudEndpoint != null && http) {
            throw UsageError("--cloud-id cannot be combined with --http")
        }

        val effectiveApiKey = when {
            !elasticApiKey.isNullOrBlank() && !apiKey.isNullOrBlank() &&
                elasticApiKey != apiKey -> throw UsageError(
                "--elastic-api-key and --api-key differ; provide only one",
            )
            !elasticApiKey.isNullOrBlank() -> elasticApiKey
            !apiKey.isNullOrBlank() -> apiKey
            else -> null
        }

        val useAwsSigV4 = if (awsSigV4) true else parseBoolean(awsSigV4FromEnv)
        if (useAwsSigV4 && (!user.isNullOrBlank() || !password.isNullOrBlank())) {
            throw UsageError("--aws-sigv4 cannot be combined with --user/--password")
        }
        if (useAwsSigV4 && !effectiveApiKey.isNullOrBlank()) {
            throw UsageError("--aws-sigv4 cannot be combined with --api-key")
        }

        currentContext.obj = ConnectionOptions(
            host = cloudEndpoint?.host ?: (host ?: "localhost"),
            port = cloudEndpoint?.port ?: (port ?: 9200),
            https = when {
                cloudEndpoint != null -> true
                http -> false
                https -> true
                else -> parseBoolean(httpsFromEnv)
            },
            cloudId = selectedCloudId,
            user = user,
            password = password,
            elasticApiKey = effectiveApiKey,
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

private data class ElasticCloudEndpoint(
    val host: String,
    val port: Int = 443,
)

@OptIn(ExperimentalEncodingApi::class)
private fun parseElasticCloudId(cloudId: String): ElasticCloudEndpoint? {
    val encoded = cloudId.substringAfter(':', cloudId).trim()
    if (encoded.isBlank()) {
        return null
    }
    val decoded = runCatching {
        Base64.Default.decode(encoded).decodeToString()
    }.recoverCatching {
        Base64.UrlSafe.decode(encoded).decodeToString()
    }.getOrNull() ?: return null

    val parts = decoded.split('$')
    if (parts.size < 3) {
        return null
    }
    val domain = parts[0].trim()
    val elasticsearchId = parts[1].trim()
    if (domain.isBlank() || elasticsearchId.isBlank()) {
        return null
    }
    return ElasticCloudEndpoint(host = "$elasticsearchId.$domain")
}
