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
import com.github.ajalt.clikt.parameters.types.choice
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.post
import com.jillesvangurp.ktsearch.root
import com.jillesvangurp.ktsearch.searchAfter
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

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

    suspend fun searchIndexRaw(
        connectionOptions: ConnectionOptions,
        index: String,
        query: String?,
        data: String?,
        size: Int,
        offset: Int,
        fields: List<String>?,
        sort: String?,
        trackTotalHits: Boolean?,
        timeout: String?,
        routing: String?,
        preference: String?,
        allowPartialResults: Boolean?,
        profile: Boolean,
        explain: Boolean,
        terminateAfter: Int?,
        searchType: String?,
    ): String
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

    override suspend fun searchIndexRaw(
        connectionOptions: ConnectionOptions,
        index: String,
        query: String?,
        data: String?,
        size: Int,
        offset: Int,
        fields: List<String>?,
        sort: String?,
        trackTotalHits: Boolean?,
        timeout: String?,
        routing: String?,
        preference: String?,
        allowPartialResults: Boolean?,
        profile: Boolean,
        explain: Boolean,
        terminateAfter: Int?,
        searchType: String?,
    ): String {
        val client = createClient(connectionOptions)
        return try {
            val body = when {
                !data.isNullOrBlank() -> withExtraProfile(data, profile)
                !query.isNullOrBlank() -> createQueryStringBody(query, profile)
                else -> null
            }
            val response = client.restClient.post {
                path(index, "_search")
                parameter("size", size)
                parameter("from", offset)
                parameter("sort", sort)
                parameter("track_total_hits", trackTotalHits)
                parameter("timeout", timeout)
                parameter("routing", routing)
                parameter("preference", preference)
                parameter("allow_partial_search_results", allowPartialResults)
                parameter("explain", explain)
                parameter("terminate_after", terminateAfter)
                parameter("search_type", searchType)
                parameter("_source_includes", fields?.joinToString(","))
                body?.let { rawBody(it) }
            }.getOrThrow()
            response.text
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

expect fun platformWriteUtf8File(path: String, content: String)

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
        subcommands(
            DumpCommand(service, platform),
            SearchCommand(service),
        )
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

class SearchCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "search") {
    override fun help(context: Context): String =
        "Run a search with lucene query string or raw JSON body."

    private val index by argument(help = "Index name to search.")

    private val query by option(
        "--query",
        help = "Lucene query string syntax.",
    )

    private val data by option(
        "--data",
        help = "Raw JSON query body.",
    )

    private val size by option(
        "--size",
        help = "Number of hits to return.",
    ).int().default(50)

    private val offset by option(
        "--offse",
        "--offset",
        help = "Offset for paging. --offse kept for compatibility.",
    ).int().default(0)

    private val fields by option(
        "--fields",
        help = "Comma-separated list of source fields to include.",
    )

    private val sort by option(
        "--sort",
        help = "Sort expression, e.g. timestamp:desc,_id:asc.",
    )

    private val trackTotalHits by option(
        "--track-total-hits",
        help = "Track total hits exactly (true|false).",
    )

    private val timeout by option(
        "--timeout",
        help = "Search timeout, e.g. 30s, 1m.",
    )

    private val routing by option(
        "--routing",
        help = "Routing value for shard targeting.",
    )

    private val preference by option(
        "--preference",
        help = "Search preference value (e.g. _local).",
    )

    private val allowPartialResults by option(
        "--allow-partial-results",
        help = "Allow partial results when shards fail (true|false).",
    )

    private val pretty by option(
        "--pretty",
        help = "Pretty-print JSON output.",
    ).flag(default = false)

    private val output by option(
        "--output",
        help = "Write output JSON to file instead of stdout.",
    )

    private val profile by option(
        "--profile",
        help = "Enable query profiling.",
    ).flag(default = false)

    private val explain by option(
        "--explain",
        help = "Include explain output for hits.",
    ).flag(default = false)

    private val terminateAfter by option(
        "--terminate-after",
        help = "Terminate search after this many docs per shard.",
    ).int()

    private val searchType by option(
        "--search-type",
        help = "Search type.",
    ).choice(
        "query_then_fetch",
        "dfs_query_then_fetch",
    )

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")
        val hasQuery = !query.isNullOrBlank()
        val hasData = !data.isNullOrBlank()
        if (hasQuery == hasData) {
            currentContext.fail(
                "Provide exactly one of --query or --data",
            )
        }

        val parsedFields = fields
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
        val parsedTrackTotalHits = parseOptionalBoolean(
            "--track-total-hits",
            trackTotalHits,
            currentContext,
        )
        val parsedAllowPartialResults = parseOptionalBoolean(
            "--allow-partial-results",
            allowPartialResults,
            currentContext,
        )

        val rawJson = service.searchIndexRaw(
            connectionOptions = connectionOptions,
            index = index,
            query = query,
            data = data,
            size = size,
            offset = offset,
            fields = parsedFields,
            sort = sort,
            trackTotalHits = parsedTrackTotalHits,
            timeout = timeout,
            routing = routing,
            preference = preference,
            allowPartialResults = parsedAllowPartialResults,
            profile = profile,
            explain = explain,
            terminateAfter = terminateAfter,
            searchType = searchType,
        )
        val outputJson = if (pretty) prettyJson(rawJson) else rawJson
        output?.let {
            platformWriteUtf8File(it, outputJson)
            echo("wrote search response to $it")
            return
        }
        echo(outputJson)
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

private fun parseOptionalBoolean(
    optionName: String,
    value: String?,
    context: Context,
): Boolean? {
    if (value == null) {
        return null
    }
    val bool = JsonPrimitive(value).booleanOrNull
    if (bool == null) {
        context.fail("$optionName must be true or false")
    }
    return bool
}

private fun createQueryStringBody(query: String, profile: Boolean): String {
    val body = buildJsonObject {
        put("query", buildJsonObject {
            put("query_string", buildJsonObject {
                put("query", JsonPrimitive(query))
            })
        })
        if (profile) {
            put("profile", JsonPrimitive(true))
        }
    }
    return body.toString()
}

private fun withExtraProfile(data: String, profile: Boolean): String {
    if (!profile) {
        return data
    }
    return try {
        val parsed = Json.Default.decodeFromString(JsonObject.serializer(), data)
        buildJsonObject {
            parsed.forEach { (k, v) -> put(k, v) }
            put("profile", JsonPrimitive(true))
        }.toString()
    } catch (_: Exception) {
        data
    }
}

private fun prettyJson(rawJson: String): String {
    return try {
        val element = Json.Default.decodeFromString(JsonElement.serializer(), rawJson)
        Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), element)
    } catch (_: Exception) {
        rawJson
    }
}

suspend fun runKtSearch(args: Array<String>) {
    KtSearchCommand().main(args)
}
