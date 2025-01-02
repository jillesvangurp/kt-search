@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jillesvangurp.ktsearch

import kotlinx.coroutines.*
import kotlin.time.Duration

@OptIn(DelicateCoroutinesApi::class)
actual fun coRun(timeout: Duration, block: suspend () -> Unit): dynamic = GlobalScope.async {
        withTimeout(timeout) {
                block.invoke()
        }
}.asPromise()


