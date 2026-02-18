package com.jillesvangurp.ktsearch.cli

import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    runBlocking {
        runKtSearch(args)
    }
}
