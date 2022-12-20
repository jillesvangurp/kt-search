package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import kotlinx.coroutines.test.TestResult
import mu.KotlinLogging
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


expect fun coRun(timeout: Duration = 30.seconds, block: suspend () -> Unit): TestResult

private val logger = KotlinLogging.logger {  }
private var versionInfo: SearchEngineInformation?=null

/**
 * Base class for search tests. Use together with the gradle compose plugin.
 *
 * It will talk to whatever runs on port 9999. That's intentionally different
 * from the default port, so you don't fill your production cluster with test data.
 */
open class SearchTestBase {
    // make sure we use the same client in all tests
    val client by lazy { SearchClient(sharedClient) }

    suspend fun onlyOn(message: String, vararg variants: SearchEngineVariant, block: suspend () -> Unit) {
        if(versionInfo==null) {
            versionInfo = client.root()
        }
        val variant = versionInfo!!.variantInfo.variant
        if(variants.contains(variant)) {
            block.invoke()
        } else {
            logger.info { "Skipping test active variant $variant is not supported. Supported [${variants.joinToString(",")}]. $message" }
        }
    }

    fun randomIndexName() = "index-${Random.nextULong()}"

    suspend fun testDocumentIndex(): String {
        val index = randomIndexName()
        return TestDocument.mapping.let {
            client.createIndex(index,it)
        }.index
    }

    val repo by lazy {
        client.repository(randomIndexName(),TestDocument.serializer())
    }

    companion object {
        private val sharedClient by lazy {
            KtorRestClient(
                nodes = arrayOf(Node("127.0.0.1", 9999)),
                client = defaultKtorHttpClient(true)
            )
        }
    }
}

