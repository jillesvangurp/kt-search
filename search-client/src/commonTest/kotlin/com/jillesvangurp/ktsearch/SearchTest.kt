package com.jillesvangurp.ktsearch

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Base class for search tests. Use together with the gradle compose plugin.
 *
 * It will talk to whatever runs on port 9999. That's intentionally different
 * than the default port so you don't fill your production cluster with test data.
 */
open class SearchTest() {
    // make sure we use the same client in all tests
    val client by lazy {  sharedClient }
    private val testScope = CoroutineScope(CoroutineName("test-scope"))
    companion object {
        private val sharedClient by lazy { KtorRestClient(nodes = arrayOf(Node("127.0.0.1", 9999))) }
    }

    fun coTest(timeout: Duration = 30.seconds, block: suspend ()->Unit ): Unit {
        testScope.launch {
            block.invoke()
        }
    }
}