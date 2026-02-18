package com.jillesvangurp.ktsearch.cli.command.index.search

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.platformWriteUtf8File
import com.jillesvangurp.ktsearch.cli.prettyJson
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

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
            currentContext.fail("Provide exactly one of --query or --data")
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
