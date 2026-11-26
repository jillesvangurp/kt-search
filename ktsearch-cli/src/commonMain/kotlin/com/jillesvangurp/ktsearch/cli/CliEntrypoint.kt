package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.context
import com.jillesvangurp.ktsearch.cli.command.root.KtSearchCommand

/** Runs the CLI, propagating a nonzero command result to the platform entry point. */
suspend fun runKtSearch(args: Array<String>) {
    val status = runKtSearchCommand(args, KtSearchCommand())
    if (status != 0) throw ProgramResult(status)
}

internal suspend fun runKtSearchCommand(args: Array<String>, command: KtSearchCommand): Int {
    var status = 0
    // CoreSuspendingCliktCommand deliberately has a no-op exit handler by default.
    command.context { exitProcess = { status = it } }
    command.main(args)
    return status
}
