package com.jillesvangurp.ktsearch.cli.command.status

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.findObject
import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.ConnectionOptions

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
