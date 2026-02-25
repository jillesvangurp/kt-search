package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.command.main
import com.jillesvangurp.ktsearch.cli.command.root.KtSearchCommand

suspend fun runKtSearch(args: Array<String>) {
    KtSearchCommand().main(args)
}
