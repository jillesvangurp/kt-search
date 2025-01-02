package com.jillesvangurp.ktsearch

import kotlin.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asPromise
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout

@OptIn(DelicateCoroutinesApi::class)
actual fun coRun(timeout: Duration, block: suspend () -> Unit) = GlobalScope.async {
    withTimeout(timeout) {
        try {
            block.invoke()
        } catch (e: Exception) {
            println("${e::class.simpleName} ${e.message}")
            error("Block failing")
        }
    }
}.asPromise()


