package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.SearchEngineVariant
import kotlinx.coroutines.test.TestResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


expect fun coRun(timeout: Duration = 30.seconds, block: suspend () -> Unit): TestResult

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
expect annotation class IgnoreJs()

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

    suspend fun testDocumentIndex(block: suspend (String) -> Unit) {
        val index = randomIndexName()
        TestDocument.mapping.let {
            client.createIndex(index,it)
        }.index
        block.invoke(index)
        client.deleteIndex(index)
    }

    val repo by lazy {
        client.repository(randomIndexName(),TestDocument.serializer())
    }

    companion object {
        private val sharedClient by lazy {
            val nodes = arrayOf(
                Node("127.0.0.1", 9999),
                Node("localhost", 9999)
            )
            KtorRestClient(
                nodes = nodes,
                client = defaultKtorHttpClient(true),
                // sniffing is a bit weird in docker, publish address is not always reachable
                nodeSelector = SniffingNodeSelector(initialNodes = nodes, maxNodeAge = 5.hours)
            )
        }
    }
}

