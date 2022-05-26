package com.jillesvangurp.ktsearch

import io.kotest.common.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

actual fun coTest(timeout: Duration, block: suspend () -> Unit) {
    runBlocking {
        withTimeout(timeout) {
            block.invoke()
        }
    }
}