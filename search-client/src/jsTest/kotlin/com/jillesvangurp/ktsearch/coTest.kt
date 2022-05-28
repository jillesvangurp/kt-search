@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jillesvangurp.ktsearch

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration

private val testScope = TestScope()
actual fun coTest(timeout: Duration, block: suspend () -> Unit): dynamic = runTest {
    block.invoke()
}


