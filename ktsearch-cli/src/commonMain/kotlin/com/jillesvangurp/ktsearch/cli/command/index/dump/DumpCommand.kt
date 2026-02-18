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

private fun isYes(input: String): Boolean {
    return input.lowercase() in setOf("y", "yes", "true", "1")
}
