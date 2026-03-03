package com.jillesvangurp.ktsearch.cli.command.index.dump

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.platformWriteUtf8File

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
        "-y",
        "--yes",
        help = "Overwrite without confirmation if file exists.",
    ).flag(default = false)

    private val schema by option(
        "--schema",
        help = "Also export <index>-schema.json with mappings and settings.",
    ).flag(default = false)

    private val aliases by option(
        "--aliases",
        help = "Also export <index>-aliases.json with aliases for index.",
    ).flag(default = false)

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")

        val outputPath = output ?: "$index.ndjson.gz"
        val outputDir = parentDir(outputPath)
        val schemaPath = joinPath(outputDir, "$index-schema.json")
        val aliasesPath = joinPath(outputDir, "$index-aliases.json")

        confirmOverwriteIfNeeded(path = outputPath)
        if (schema) {
            confirmOverwriteIfNeeded(path = schemaPath)
        }
        if (aliases) {
            confirmOverwriteIfNeeded(path = aliasesPath)
        }

        val writer = platform.createGzipWriter(outputPath)
        val lines = try {
            service.dumpIndex(connectionOptions, index, writer)
        } finally {
            writer.close()
        }

        echo("wrote $lines lines to $outputPath")
        if (schema) {
            val schemaJson = service.exportIndexSchema(connectionOptions, index)
            platformWriteUtf8File(schemaPath, schemaJson)
            echo("wrote schema to $schemaPath")
        }
        if (aliases) {
            val aliasesJson = service.exportIndexAliases(connectionOptions, index)
            platformWriteUtf8File(aliasesPath, aliasesJson)
            echo("wrote aliases to $aliasesPath")
        }
    }

    private fun confirmOverwriteIfNeeded(path: String) {
        if (!platform.fileExists(path) || yes) {
            return
        }
        if (!platform.isInteractiveInput()) {
            currentContext.fail(
                "$path exists; use --yes to overwrite in non-interactive mode",
            )
        }
        echo("$path exists. Overwrite? [y/N]")
        val answer = platform.readLineFromStdin()?.trim().orEmpty()
        if (!isYes(answer)) {
            currentContext.fail("Aborted. File exists: $path")
        }
    }
}

private fun isYes(input: String): Boolean {
    return input.lowercase() in setOf("y", "yes", "true", "1")
}
