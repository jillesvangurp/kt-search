package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.ClusterStatus
import com.jillesvangurp.ktsearch.ClusterHealthResponse
import com.jillesvangurp.ktsearch.HttpMethod
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.RestClient
import com.jillesvangurp.ktsearch.RestResponse
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.SearchResponse
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.notification
import com.jillesvangurp.ktsearch.alert.rules.RuleAlertStatus
import com.jillesvangurp.ktsearch.alert.rules.clusterStatusRule
import com.jillesvangurp.ktsearch.alert.rules.newSearchRule
import com.jillesvangurp.searchdsls.querydsl.matchAll
import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private const val ONE_MINUTE_MS = 60_000L

class AlertServiceEndToEndTest {

    @Test
    fun search_rule_triggers_notifications() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val restClient = FakeRestClient { request ->
            when (request.normalizedPath) {
                "_cluster/health" -> ok(clusterHealthResponseJson(ClusterStatus.Green))
                "logs-app/_search" -> ok(searchResponseJson(matchCount = 2))
                else -> error("Unexpected path ${request.normalizedPath}")
            }
        }
        val client = SearchClient(restClient = restClient)
        val service = AlertService(
            client = client,
            nowProvider = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
            dispatcherContext = dispatcher
        )

        val recorder = RecordingNotification("alert")
        val configuration = alertConfiguration {
            notifications { +recorder.definition }
            rules {
                defaultNotificationIds("alert")
                +newSearchRule(
                    id = "logs-search",
                    cronExpression = "* * * * *",
                    target = "logs-app"
                ) {
                    query = matchAll()
                }
            }
        }

        service.start(configuration)
        testScheduler.advanceTimeBy(ONE_MINUTE_MS)
        runCurrent()

        recorder.contexts.shouldHaveSize(1)
        recorder.payloads.shouldHaveSize(1)

        val context = recorder.contexts.single()
        context.ruleId shouldBe "logs-search"
        context.matchCount shouldBe 2
        context.matches.shouldHaveSize(2)
        context.totalMatchCount shouldBe 2
        context.resultDescription shouldBe "Search alert for 'logs-app' triggered with 2 documents (showing all 2 documents)"
        context.problemDetails shouldBe "Found 2 documents for 'logs-app', exceeding the limit of 0; showing all 2 documents."

        val variables = recorder.payloads.single()
        variables["ruleId"] shouldBe "logs-search"
        variables["ruleName"] shouldBe "logs-search"
        variables["matchCount"] shouldBe "2"
        variables["status"] shouldBe "SUCCESS"
        variables["target"] shouldBe "logs-app"
        variables["matchesJson"]?.contains("\"value\"") shouldBe true
        variables["resultDescription"] shouldBe "Search alert for 'logs-app' triggered with 2 documents (showing all 2 documents)"
        variables["problemDetails"] shouldBe "Found 2 documents for 'logs-app', exceeding the limit of 0; showing all 2 documents."

        val currentRule = service.currentRules().single()
        currentRule.alertStatus shouldBe RuleAlertStatus.ALERTING
        currentRule.lastNotificationAt shouldBe Instant.fromEpochMilliseconds(testScheduler.currentTime)

        service.stop()
    }

    @Test
    fun cluster_status_rule_reports_health_mismatches() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var clusterStatus = ClusterStatus.Red
        val restClient = FakeRestClient { request ->
            when (request.normalizedPath) {
                "_cluster/health" -> ok(clusterHealthResponseJson(clusterStatus))
                else -> error("Unexpected path ${request.normalizedPath}")
            }
        }
        val client = SearchClient(restClient = restClient)
        val service = AlertService(
            client = client,
            nowProvider = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
            dispatcherContext = dispatcher
        )

        val recorder = RecordingNotification("cluster-alert")
        val configuration = alertConfiguration {
            notifications { +recorder.definition }
            rules {
                defaultNotificationIds("cluster-alert")
                +clusterStatusRule(
                    id = "cluster-health",
                    cronExpression = "* * * * *",
                    expectedStatus = ClusterStatus.Green,
                    description = "production"
                )
            }
        }

        service.start(configuration)
        testScheduler.advanceTimeBy(ONE_MINUTE_MS)
        runCurrent()

        recorder.contexts.shouldHaveSize(1)
        recorder.payloads.shouldHaveSize(1)

        val context = recorder.contexts.single()
        context.ruleId shouldBe "cluster-health"
        context.matchCount shouldBe 1
        context.matches.shouldHaveSize(1)
        context.totalMatchCount shouldBe 1
        val payload = context.matches.single()
        payload["expectedStatus"] shouldBe JsonPrimitive("green")
        payload["actualStatus"] shouldBe JsonPrimitive("red")
        payload["clusterName"] shouldBe JsonPrimitive("test-cluster")
        context.resultDescription shouldBe "production status is red (expected green)"
        context.problemDetails shouldBe "Cluster 'test-cluster' reports red status; expected green."

        val variables = recorder.payloads.single()
        variables["status"] shouldBe "SUCCESS"
        variables["target"] shouldBe "production"
        variables["resultDescription"] shouldBe "production status is red (expected green)"
        variables["problemDetails"] shouldBe "Cluster 'test-cluster' reports red status; expected green."

        val currentRule = service.currentRules().single()
        currentRule.alertStatus shouldBe RuleAlertStatus.ALERTING

        // Simulate cluster recovering and ensure rule clears on next evaluation.
        clusterStatus = ClusterStatus.Green
        testScheduler.advanceTimeBy(ONE_MINUTE_MS)
        runCurrent()

        recorder.contexts.shouldHaveSize(1) // no new notifications when cluster healthy
        val updatedRule = service.currentRules().single()
        updatedRule.alertStatus shouldBe RuleAlertStatus.CLEAR
        updatedRule.lastNotificationAt shouldBe null

        service.stop()
    }

    @Test
    fun console_notification_logs_after_failure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var searchAttempts = 0
        val restClient = FakeRestClient { request ->
            when (request.normalizedPath) {
                "_cluster/health" -> ok(clusterHealthResponseJson(ClusterStatus.Green))
                "logs-app/_search" -> {
                    searchAttempts++
                    if (searchAttempts == 1) {
                        throw IllegalStateException("search failed")
                    } else {
                        ok(searchResponseJson(matchCount = 1))
                    }
                }
                else -> error("Unexpected path ${request.normalizedPath}")
            }
        }
        val client = SearchClient(restClient = restClient)
        val service = AlertService(
            client = client,
            nowProvider = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
            dispatcherContext = dispatcher
        )

        val recorder = RecordingNotification("alert")
        val configuration = alertConfiguration {
            notifications { +recorder.definition }
            rules {
                defaultNotificationIds("alert")
                +newSearchRule(
                    id = "logs-search",
                    cronExpression = "* * * * *",
                    target = "logs-app"
                ) {
                    query = matchAll()
                }
            }
        }

        service.start(configuration)
        testScheduler.advanceTimeBy(ONE_MINUTE_MS)
        runCurrent()

        recorder.contexts.shouldHaveSize(1)
        recorder.payloads.shouldHaveSize(1)
        recorder.payloads.first()["status"] shouldBe "FAILURE"

        testScheduler.advanceTimeBy(ONE_MINUTE_MS)
        runCurrent()

        recorder.contexts.shouldHaveSize(2)
        recorder.payloads.last()["status"] shouldBe "SUCCESS"

        service.stop()
    }

    private class FakeRestClient(
        private val handler: suspend (Request) -> RestResponse
    ) : RestClient {
        data class Request(
            val method: HttpMethod,
            val path: String,
            val parameters: Map<String, Any>?,
            val payload: String?,
            val headers: Map<String, Any>?,
            val contentType: String
        ) {
            val normalizedPath: String = path.removePrefix("/")
        }

        override suspend fun nextNode(): Node = Node("fake", 9200)

        override fun close() {}

        override suspend fun doRequest(
            pathComponents: List<String>,
            httpMethod: HttpMethod,
            parameters: Map<String, Any>?,
            payload: String?,
            contentType: String,
            headers: Map<String, Any>?
        ): RestResponse {
            val path = pathComponents.joinToString("/")
            return handler(
                Request(
                    method = httpMethod,
                    path = path,
                    parameters = parameters,
                    payload = payload,
                    headers = headers,
                    contentType = contentType
                )
            )
        }
    }

    private class RecordingNotification(id: String) {
        val contexts = mutableListOf<NotificationContext>()
        val payloads = mutableListOf<Map<String, String>>()
        val definition = notification(id) {
            contexts += context
            payloads += this.variables.toMap()
        }
    }

    private fun ok(payload: String): RestResponse =
        RestResponse.Status2XX.OK(payload.toByteArray())

    private fun searchResponseJson(matchCount: Int): String {
        val hits = (1..matchCount).map { index ->
            SearchResponse.Hit(
                index = "logs",
                type = null,
                id = index.toString(),
                score = 1.0,
                source = buildJsonObject { put("value", JsonPrimitive(index)) },
                fields = null,
                sort = null,
                innerHits = null,
                highlight = null,
                seqNo = null,
                primaryTerm = null,
                version = null,
                explanation = null,
                matchedQueries = null
            )
        }
        val response = SearchResponse(
            took = 5,
            shards = null,
            timedOut = false,
            hits = SearchResponse.Hits(
                maxScore = null,
                total = SearchResponse.Hits.Total(
                    value = matchCount.toLong(),
                    relation = SearchResponse.Hits.TotalRelation.Eq
                ),
                hits = hits
            ),
            aggregations = null,
            scrollId = null,
            pitId = null,
            pointInTimeId = null,
            suggest = null
        )
        return DEFAULT_JSON.encodeToString(response)
    }

    private fun clusterHealthResponseJson(status: ClusterStatus): String {
        val response = ClusterHealthResponse(
            clusterName = "test-cluster",
            status = status,
            timedOut = false
        )
        return DEFAULT_JSON.encodeToString(response)
    }
}
