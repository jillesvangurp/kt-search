//package com.jillesvangurp.ktsearch
//
//import kotlinx.coroutines.*
//import kotlinx.coroutines.test.TestResult
//import kotlin.time.Duration
//
//private val testScope = CoroutineScope(CoroutineName("test-scope"))
//actual fun coTest(timeout: Duration, block: suspend () -> Unit): TestResult {
//    return testScope.async {
//        withTimeout(timeout) {
//            block.invoke()
//        }
//    }.asPromise()
//}
//
//
