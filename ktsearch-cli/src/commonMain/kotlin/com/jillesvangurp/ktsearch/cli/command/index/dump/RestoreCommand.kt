package com.jillesvangurp.ktsearch.cli.command.index.dump

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.platformReadUtf8File

class RestoreCommand(
    private val service: CliService,
    private val platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "restore") {
    override fun help(context: Context): String =
        "Restore gzipped NDJSON dump into an index."

    private val index by argument(help = "Target index name.")

    private val input by option(
        "--input",
        help = "Input file path. Defaults to <index>.ndjson.gz.",
    )

    private val bulkSize by option(
        "--bulk-size",
        help = "Bulk request size.",
    ).int().default(500)

    private val recreate by option(
        "--recreate",
        help = "Delete and recreate target index before restore.",
    ).flag(default = false)

    private val createIfMissing by option(
        "--create-if-missing",
        help = "Create target index when missing (default true).",
    ).choice("true", "false").default("true")

    private val refresh by option(
        "--refresh",
        help = "Refresh mode: wait_for|true|false.",
    ).choice("wait_for", "true", "false").default("wait_for")

    private val pipeline by option(
        "--pipeline",
        help = "Ingest pipeline for restore indexing.",
    )

    private val routing by option(
        "--routing",
        help = "Routing value for all restored docs.",
    )

    private val idField by option(
        "--id-field",
        help = "Extract document id from this JSON field per line.",
    )
    private val disableRefreshInterval by option(
        "--disable-refresh-interval",
        help = "Temporarily set index refresh_interval to -1.",
    ).flag(default = false)

    private val setReplicasToZero by option(
        "--set-replicas-zero",
        help = "Temporarily set index number_of_replicas to 0.",
    ).flag(default = false)

    private val schema by option(
        "--schema",
        help = "Load <index>-schema.json and create target index from it.",
    ).flag(default = false)

    private val aliases by option(
        "--aliases",
        help = "Load <index>-aliases.json and apply aliases before restore.",
    ).flag(default = false)

    private val yes by option(
        "-y",
        "--yes",
        help = "Do not prompt for destructive actions.",
    ).flag(default = false)

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")
        val inputPath = input ?: "$index.ndjson.gz"
        val inputDir = parentDir(inputPath)
        val schemaPath = joinPath(inputDir, "$index-schema.json")
        val aliasesPath = joinPath(inputDir, "$index-aliases.json")

        if (!platform.fileExists(inputPath)) {
            currentContext.fail("Input file does not exist: $inputPath")
        }
        val schemaJson = if (schema) {
            if (!platform.fileExists(schemaPath)) {
                currentContext.fail("Schema file does not exist: $schemaPath")
            }
            platformReadUtf8File(schemaPath)
        } else {
            null
        }
        val aliasesJson = if (aliases) {
            if (!platform.fileExists(aliasesPath)) {
                currentContext.fail("Aliases file does not exist: $aliasesPath")
            }
            platformReadUtf8File(aliasesPath)
        } else {
            null
        }

        if (recreate && !yes) {
            if (!platform.isInteractiveInput()) {
                currentContext.fail(
                    "Use --yes with --recreate in non-interactive mode",
                )
            }
            echo("Recreate index '$index' before restore? [y/N]")
            val answer = platform.readLineFromStdin()?.trim().orEmpty()
            if (answer.lowercase() !in setOf("y", "yes", "true", "1")) {
                currentContext.fail("Aborted")
            }
        }

        val reader = platform.createGzipReader(inputPath)
        val lines = try {
            service.restoreIndex(
                connectionOptions = connectionOptions,
                index = index,
                reader = reader,
                bulkSize = bulkSize,
                createIfMissing = createIfMissing == "true",
                recreate = recreate,
                refresh = refresh,
                pipeline = pipeline,
                routing = routing,
                idField = idField,
                disableRefreshInterval = disableRefreshInterval,
                setReplicasToZero = setReplicasToZero,
                schemaJson = schemaJson,
                aliasesJson = aliasesJson,
            )
        } finally {
            reader.close()
        }

        echo("restored $lines documents into $index from $inputPath")
    }
}
