package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.platformReadUtf8File
import com.jillesvangurp.ktsearch.cli.prettyJson

internal fun requireConfirmation(
    context: Context,
    platform: CliPlatform,
    yes: Boolean,
    prompt: String,
) {
    if (yes) {
        return
    }
    if (!platform.isInteractiveInput()) {
        context.fail("Refusing without --yes in non-interactive mode")
    }
    println("$prompt [y/N]")
    val answer = platform.readLineFromStdin()?.trim().orEmpty().lowercase()
    if (answer !in setOf("y", "yes", "true", "1")) {
        context.fail("Aborted")
    }
}

internal fun readBody(
    data: String?,
    file: String?,
    required: Boolean,
    context: Context,
): String? {
    if (!data.isNullOrBlank() && !file.isNullOrBlank()) {
        context.fail("Provide only one of --data or --file")
    }
    val body = when {
        !data.isNullOrBlank() -> data
        !file.isNullOrBlank() -> platformReadUtf8File(file)
        else -> null
    }
    if (required && body.isNullOrBlank()) {
        context.fail("Provide one of --data or --file")
    }
    return body
}

internal fun CoreSuspendingCliktCommand.requireConnectionOptions() =
    currentContext.findObject<ConnectionOptions>()
        ?: error("Missing connection options in command context")

internal fun CoreSuspendingCliktCommand.prettyFlag(default: Boolean = true) =
    option("--pretty", help = "Pretty-print JSON output.")
        .flag("--no-pretty", default = default)

internal fun CoreSuspendingCliktCommand.yesFlag() =
    option("-y", "--yes", help = "Do not prompt.")
        .flag(default = false)

internal fun CoreSuspendingCliktCommand.dryRunFlag(
    help: String = "Preview request without executing.",
) = option("--dry-run", help = help)
    .flag(default = false)

internal fun CoreSuspendingCliktCommand.echoJson(
    response: String,
    pretty: Boolean,
) {
    echo(if (pretty) prettyJson(response) else response)
}
