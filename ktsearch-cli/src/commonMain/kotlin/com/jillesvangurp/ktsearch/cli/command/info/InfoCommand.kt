package com.jillesvangurp.ktsearch.cli.command.info

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.findObject
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions
import com.jillesvangurp.ktsearch.cli.output.JsonOutputRenderer
import com.jillesvangurp.ktsearch.cli.output.OutputOptions

class InfoCommand(
    private val service: CliService,
) : CoreSuspendingCliktCommand(name = "info") {
    override fun help(context: Context): String =
        "Show cluster details from GET /."

    private val outputOptions by OutputOptions()

    override suspend fun run() {
        val connectionOptions = currentContext.findObject<ConnectionOptions>()
            ?: error("Missing connection options in command context")

        val raw = service.fetchRootInfo(connectionOptions)
        val output = JsonOutputRenderer.renderTableOrRaw(
            rawJson = raw,
            outputFormat = outputOptions.outputFormat,
        )
        echo(output)
    }
}
