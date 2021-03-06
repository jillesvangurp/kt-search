@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jillesvangurp.ktsearch

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration

actual fun coTest(timeout: Duration, block: suspend () -> Unit): dynamic = GlobalScope.async {
        block.invoke()
}.asPromise()


