package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SniffingNodeSelector
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.NotificationVariable
import com.jillesvangurp.ktsearch.alert.notifications.notification
import com.jillesvangurp.ktsearch.alert.rules.AlertRuleDefinition
import com.jillesvangurp.ktsearch.defaultKtorHttpClient
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.match
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

        val recorder = RecordingNotification()
        val service = AlertService(client)
        val configuration = alertConfiguration {
            notification(
                recorder.definition("ops-email")
            )
            defaultNotifications("ops-email")
            rule(
                AlertRuleDefinition.newRule(
                    id = "test-alert",
                    name = "Error monitor",
                    cronExpression = "* * * * *",
                    target = docsIndex,
                    message = "Disk space issues detected",
                    notifications = emptyList()
                ) {
                    query = match(LogEntry::level, "error")
                }
            )
        }

        try {
            service.start(configuration)
            val (notification, variables) = withTimeout(30_000) { recorder.await() }

            notification.ruleId shouldBe "test-alert"
            notification.matchCount shouldBe 1
            notification.matches.shouldHaveSize(1)
            notification.resultDescription shouldBe "Search alert for '$docsIndex' triggered with 1 result"
            variables[NotificationVariable.RULE_NAME.key] shouldBe "Error monitor"
            variables[NotificationVariable.RULE_MESSAGE.key] shouldBe "Disk space issues detected"
            variables[NotificationVariable.RESULT_DESCRIPTION.key] shouldBe "Search alert for '$docsIndex' triggered with 1 result"
            val matchesJson = variables[NotificationVariable.MATCHES_JSON.key]
            checkNotNull(matchesJson) { "matchesJson variable should be present" }
            val parsedMatches = Json.parseToJsonElement(matchesJson).jsonArray
            parsedMatches.shouldHaveSize(1)
            parsedMatches.first().jsonObject["message"]?.jsonPrimitive?.content shouldBe "disk full"
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

    private class RecordingNotification {
        private val deferred = CompletableDeferred<Pair<NotificationContext, Map<String, String>>>()
        suspend fun await(): Pair<NotificationContext, Map<String, String>> = deferred.await()

        fun definition(id: String) = notification(id) {
            if (!deferred.isCompleted) {
                deferred.complete(context to variables)
            }
        }
    }
}
