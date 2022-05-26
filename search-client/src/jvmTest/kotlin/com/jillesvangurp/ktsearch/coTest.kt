package com.jillesvangurp.ktsearch

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

actual fun coTest(timeout: Duration, block: suspend () -> Unit) {
    runBlocking {
        withTimeout(timeout) {
            block.invoke()
        }
    }
}