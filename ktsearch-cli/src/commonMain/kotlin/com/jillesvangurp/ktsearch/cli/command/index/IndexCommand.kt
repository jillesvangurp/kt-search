package com.jillesvangurp.ktsearch.cli.command.index

import com.github.ajalt.clikt.command.CoreSuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.jillesvangurp.ktsearch.cli.CliPlatform
import com.jillesvangurp.ktsearch.cli.CliService
import com.jillesvangurp.ktsearch.cli.command.index.dump.RestoreCommand
import com.jillesvangurp.ktsearch.cli.command.index.dump.DumpCommand
import com.jillesvangurp.ktsearch.cli.command.index.search.SearchCommand

class IndexCommand(
    service: CliService,
    platform: CliPlatform,
) : CoreSuspendingCliktCommand(name = "index") {
    override fun help(context: Context): String = "Index-related commands."

    override val invokeWithoutSubcommand: Boolean = true

    init {
        subcommands(
            CreateIndexCommand(service),
            CreateIndexCommand(service, name = "put"),
            GetIndexCommand(service),
            GetIndexCommand(service, name = "show"),
            IndexExistsCommand(service),
            RefreshIndexCommand(service),
            DeleteIndexCommand(service, platform),
            DeleteIndexCommand(service, platform, name = "rm"),
            DocCommand(service, platform),
            MappingsCommand(service),
            SettingsCommand(service),
            TemplateCommand(service, platform),
            DataStreamCommand(service, platform),
            AliasCommand(service, platform),
            SnapshotCommand(service, platform),
            ReindexCommand(service),
            ReindexTaskStatusCommand(service),
            ReindexWaitCommand(service),
            IlmCommand(service, platform),
            ApplyCommand(service),
            WaitGreenCommand(service),
            WaitExistsCommand(service),
            DumpCommand(service, platform),
            RestoreCommand(service, platform),
            SearchCommand(service),
        )
    }

    override suspend fun run() {
        if (currentContext.invokedSubcommand == null) {
            echoFormattedHelp()
        }
    }
}
