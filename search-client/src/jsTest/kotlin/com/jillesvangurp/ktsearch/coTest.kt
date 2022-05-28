package com.jillesvangurp.ktsearch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
actual fun coTest(timeout: Duration, block: suspend () -> Unit) = runTest {
    block.invoke()
}


