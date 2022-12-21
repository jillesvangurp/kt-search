@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jillesvangurp.ktsearch

import kotlinx.coroutines.*
import kotlin.time.Duration

actual fun coRun(timeout: Duration, block: suspend () -> Unit): dynamic = GlobalScope.async {
        block.invoke()
}.asPromise()


