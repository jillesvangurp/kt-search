package com.jillesvangurp.ktsearch

import kotlinx.coroutines.*
import kotlin.time.Duration

private val testScope = CoroutineScope(CoroutineName("test-scope"))
actual fun coTest(timeout: Duration, block: suspend () -> Unit): dynamic = testScope.async {
    withTimeout(timeout) {
        block.invoke()
    }
}.asPromise()


