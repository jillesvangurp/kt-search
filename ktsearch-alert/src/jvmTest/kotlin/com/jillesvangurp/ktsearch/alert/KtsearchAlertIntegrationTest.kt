package com.jillesvangurp.ktsearch.alert

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SniffingNodeSelector
import com.jillesvangurp.ktsearch.defaultKtorHttpClient
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.match
import kotlin.random.Random
import kotlin.math.absoluteValue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.URI

class KtsearchAlertIntegrationTest {
    private val client: SearchClient = createClient()
    private val resources = mutableListOf<String>()

    @AfterTest
    fun tearDown() = runBlocking {
        resources.forEach { index ->
            runCatching { client.deleteIndex(index) }
        }
        client.close()
    }

    @Test
    fun `should trigger email when query matches`() = runBlocking {
        assumeTrue(clusterAvailable(), "Search cluster is not available on localhost:9999")
        val alertsIndex = randomIndex("alerts")
        val docsIndex = randomIndex("docs")
        resources += alertsIndex
        resources += docsIndex

        val logRepository = client.repository(docsIndex, LogEntry.serializer())
        runCatching { client.deleteIndex(docsIndex) }
        logRepository.createIndex(docsIndex) {
            mappings(dynamicEnabled = true) {
                keyword(LogEntry::level)
                keyword(LogEntry::message)
            }
        }
        logRepository.index(LogEntry(level = "error", message = "disk full"), id = "1")

        val plugin = RecordingSendPlugin()
        val alert = KtsearchAlert(client, plugin, alertsIndex)
        try {
            alert.start()

            alert.upsertRules {
                rule("test-alert") {
                    name = "Error monitor"
                    target(docsIndex)
                    cron("* * * * *")
                    query {
                        query = match(LogEntry::level, "error")
                    }
                    email {
                        from("alerts@example.com")
                        to("ops@example.com")
                        subject = "{{ruleName}} triggered"
                        body = "Found {{matchCount}} error events"
                    }
                }
            }

            val context = withTimeout(30_000) { plugin.await() }
            assertEquals("test-alert", context.rule.id)
            assertTrue(context.matches.isNotEmpty())
            assertEquals("Error monitor", context.rule.name)
        } finally {
            alert.stop()
        }
    }

    private fun randomIndex(prefix: String): String {
        val randomValue = Random.nextLong()
        val sanitized = if (randomValue == Long.MIN_VALUE) 0 else randomValue.absoluteValue
        return "$prefix-$sanitized"
    }

    private fun clusterAvailable(): Boolean =
        runCatching {
            URI("http://localhost:9999").toURL().openConnection().apply {
                connectTimeout = 1_000
                readTimeout = 1_000
            }.connect()
            true
        }.getOrElse { false }

    private fun createClient(): SearchClient {
        val nodes = arrayOf(
            Node("127.0.0.1", 9999),
            Node("localhost", 9999)
        )
        val restClient = KtorRestClient(
            nodes = nodes,
            client = defaultKtorHttpClient(true) {},
            nodeSelector = SniffingNodeSelector(initialNodes = nodes)
        )
        return SearchClient(restClient)
    }

    @Serializable
    data class LogEntry(
        val level: String,
        val message: String
    )

    private class RecordingSendPlugin : AlertSendPlugin {
        private val deferred = CompletableDeferred<AlertSendContext>()

        suspend fun await(): AlertSendContext = deferred.await()

        override suspend fun send(renderedEmail: RenderedEmail, context: AlertSendContext) {
            if (!deferred.isCompleted) {
                deferred.complete(context)
            }
        }
    }
}
