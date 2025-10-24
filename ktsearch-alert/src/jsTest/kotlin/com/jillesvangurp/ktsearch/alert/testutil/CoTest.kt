package com.jillesvangurp.ktsearch.alert.testutil

import kotlin.time.Duration
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asPromise
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout

@OptIn(DelicateCoroutinesApi::class)
actual fun coRun(timeout: Duration, block: suspend () -> Unit) {
    GlobalScope.async {
        withTimeout(timeout) {
            block()
        }
    }.asPromise()
}
