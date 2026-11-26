package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.core.ProgramResult
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val status = runBlocking {
        try {
            runKtSearch(args)
            0
        } catch (result: ProgramResult) {
            result.statusCode
        }
    }
    if (status != 0) exitProcess(status)
}
