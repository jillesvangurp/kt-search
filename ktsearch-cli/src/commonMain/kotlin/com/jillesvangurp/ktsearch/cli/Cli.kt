package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.completion.SuspendingCompletionCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.root
import com.jillesvangurp.ktsearch.searchAfter
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.JsonObject

/** Shared connection options used by all CLI commands. */
data class ConnectionOptions(
    val host: String,
    val port: Int,
    val https: Boolean,
    val user: String?,
    val password: String?,
    val logging: Boolean,
)

/** Result object for cluster status checks. */
data class StatusResult(
    val clusterName: String,
    val status: ClusterStatus,
    val timedOut: Boolean,
)

/** Small service abstraction to keep command tests hermetic. */
interface CliService {
    suspend fun fetchStatus(connectionOptions: ConnectionOptions): StatusResult

    suspend fun dumpIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        writer: NdjsonGzipWriter,
    ): Long
}

/** Default service implementation backed by [SearchClient]. */
class DefaultCliService : CliService {
    override suspend fun fetchStatus(connectionOptions: ConnectionOptions): StatusResult {
        val client = createClient(connectionOptions)
        try {
            val root = client.root()
            val health = client.clusterHealth()
            return StatusResult(
                clusterName = if (health.clusterName.isNotBlank()) {
                    health.clusterName
                } else {
                    root.clusterName
                },
                status = health.status,
                timedOut = health.timedOut,
            )
        } finally {
            client.close()
        }
    }

    override suspend fun dumpIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        writer: NdjsonGzipWriter,
    ): Long {
        val client = createClient(connectionOptions)
        return try {
            var lines = 0L
            val (_, flow) = client.searchAfter(index, keepAlive = 1.minutes) {
                resultSize = 500
            }
            flow.collect { hit ->
                val source = hit.source
                    ?: error("Hit ${hit.index}/${hit.id} has no _source")
                val line = client.json.encodeToString(JsonObject.serializer(), source)
                writer.writeLine(line)
                lines++
            }
            lines
        } finally {
            client.close()
        }
    }

    private fun createClient(connectionOptions: ConnectionOptions): SearchClient {
        return SearchClient(
            KtorRestClient(
                host = connectionOptions.host,
                port = connectionOptions.port,
                https = connectionOptions.https,
                user = connectionOptions.user,
                password = connectionOptions.password,
                logging = connectionOptions.logging,
            )
        )
    }
}

interface CliPlatform {
    fun fileExists(path: String): Boolean

    fun isInteractiveInput(): Boolean

    fun readLineFromStdin(): String?

    fun createGzipWriter(path: String): NdjsonGzipWriter
}

expect fun platformFileExists(path: String): Boolean

expect fun platformIsInteractiveInput(): Boolean

expect fun platformReadLineFromStdin(): String?

expect fun platformCreateGzipWriter(path: String): NdjsonGzipWriter

interface NdjsonGzipWriter {
    fun writeLine(line: String)

    fun close()
}

object DefaultCliPlatform : CliPlatform {
    override fun fileExists(path: String): Boolean = platformFileExists(path)

    override fun isInteractiveInput(): Boolean = platformIsInteractiveInput()

    override fun readLineFromStdin(): String? = platformReadLineFromStdin()

    override fun createGzipWriter(path: String): NdjsonGzipWriter =
        platformCreateGzipWriter(path)
}

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
            StatusCommand(service),
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

class StatusCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "status") {
    override fun help(context: Context): String =
        "Check cluster name and health color."

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")

        val status = service.fetchStatus(connectionOptions)
        val isGreen = status.status == ClusterStatus.Green

        echo(
            "cluster=${status.clusterName} " +
                "status=${status.status.name.lowercase()} " +
                "green=$isGreen",
        )

        if (status.timedOut || status.status == ClusterStatus.Red) {
            throw ProgramResult(2)
        }
    }
}

class IndexCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "index") {
    override fun help(context: Context): String = "Index-related commands."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(DumpCommand(service, platform))
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}

class DumpCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "dump") {
    override fun help(context: Context): String =
        "Dump all index documents to gzipped NDJSON using search_after."

    private val index by argument(help = "Index name to dump.")

    private val output by option(
        "--output",
        help = "Output file path. Defaults to <index>.ndjson.gz.",
    )

    private val yes by option(
        "--yes",
        help = "Overwrite without confirmation if file exists.",
    ).flag(default = false)

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")

        val outputPath = output ?: "$index.ndjson.gz"

        if (platform.fileExists(outputPath) && !yes) {
            if (!platform.isInteractiveInput()) {
                currentContext.fail(
                    "$outputPath exists; use --yes to overwrite in non-interactive mode",
                )
            }
            echo("$outputPath exists. Overwrite? [y/N]")
            val answer = platform.readLineFromStdin()?.trim().orEmpty()
            if (!isYes(answer)) {
                currentContext.fail("Aborted. File exists: $outputPath")
            }
        }

        val writer = platform.createGzipWriter(outputPath)
        val lines = try {
            service.dumpIndex(connectionOptions, index, writer)
        } finally {
            writer.close()
        }

        echo("wrote $lines lines to $outputPath")
    }
}

private fun parseBoolean(value: String?): Boolean {
    return when (value?.trim()?.lowercase()) {
        "1", "true", "yes", "y", "on" -> true
        else -> false
    }
}

private fun isYes(input: String): Boolean {
    return input.lowercase() in setOf("y", "yes", "true", "1")
}

suspend fun runKtSearch(args: Array<String>) {
    KtSearchCommand().main(args)
}
