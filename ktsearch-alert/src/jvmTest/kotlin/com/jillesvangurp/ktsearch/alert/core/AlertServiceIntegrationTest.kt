package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SniffingNodeSelector
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.alert.notifications.NotificationChannel
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDispatcher
import com.jillesvangurp.ktsearch.alert.notifications.NotificationHandler
import com.jillesvangurp.ktsearch.defaultKtorHttpClient
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.match
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

class AlertServiceIntegrationTest {
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
    fun `should trigger notification when query matches`(): Unit = runBlocking {
        val docsIndex = randomIndex("docs")
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

        val handler = RecordingNotificationHandler()
        val dispatcher = NotificationDispatcher(listOf(handler))
        val service = AlertService(client, dispatcher)
        val configuration = alertConfiguration {
            notifications {
                email("ops-email") {
                    from("alerts@example.com")
                    to("ops@example.com")
                    subject = "{{ruleName}} triggered"
                    body = "Found {{matchCount}} error events"
                }
            }
            rules {
                rule("test-alert") {
                    name = "Error monitor"
                    target(docsIndex)
                    cron("* * * * *")
                    query {
                        query = match(LogEntry::level, "error")
                    }
                    notifications("ops-email")
                }
            }
        }

        try {
            service.start(configuration)
            val (notification, variables) = withTimeout(30_000) { handler.await() }

            notification.ruleId shouldBe "test-alert"
            notification.matchCount shouldBe 1
            notification.matches.shouldNotBeEmpty()
            variables["ruleName"] shouldBe "Error monitor"
        } finally {
            service.stop()
        }
    }

    private fun randomIndex(prefix: String): String {
        val randomValue = Random.nextLong()
        val sanitized = if (randomValue == Long.MIN_VALUE) 0 else randomValue.absoluteValue
        return "$prefix-$sanitized"
    }

    private fun createClient(): SearchClient {
        val nodes = arrayOf(Node("127.0.0.1", 9999), Node("localhost", 9999))
        val restClient = KtorRestClient(
            nodes = nodes,
            client = defaultKtorHttpClient(true) {},
            nodeSelector = SniffingNodeSelector(initialNodes = nodes)
        )
        return SearchClient(restClient)
    }

    @Serializable
    data class LogEntry(val level: String, val message: String)

    private class RecordingNotificationHandler : NotificationHandler {
        private val deferred = CompletableDeferred<Pair<NotificationContext, Map<String, String>>>()
        override val channel: NotificationChannel = NotificationChannel.EMAIL
        suspend fun await(): Pair<NotificationContext, Map<String, String>> = deferred.await()

        override suspend fun send(
            definition: NotificationDefinition,
            variables: Map<String, String>,
            context: NotificationContext
        ) {
            if (!deferred.isCompleted) {
                deferred.complete(context to variables)
            }
        }
    }
}
