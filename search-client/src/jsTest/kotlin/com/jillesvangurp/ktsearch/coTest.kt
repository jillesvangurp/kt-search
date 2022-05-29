package com.jillesvangurp.ktsearch

import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration

//private val testScope = TestScope()
actual fun coTest(timeout: Duration, block: suspend () -> Unit): dynamic = GlobalScope.async {
        block.invoke()
}.asPromise()


