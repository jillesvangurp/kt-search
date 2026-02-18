package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.platformReadUtf8File

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
