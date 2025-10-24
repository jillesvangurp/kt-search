package com.jillesvangurp.ktsearch.alert.core

import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.RestClient
import com.jillesvangurp.ktsearch.RestResponse
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.alert.notifications.NotificationChannel
import com.jillesvangurp.ktsearch.alert.notifications.NotificationContext
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDefinition
import com.jillesvangurp.ktsearch.alert.notifications.NotificationDispatcher
import com.jillesvangurp.ktsearch.alert.notifications.NotificationHandler
import com.jillesvangurp.ktsearch.alert.notifications.NotificationVariable
import com.jillesvangurp.searchdsls.querydsl.matchAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class AlertServiceFailureTest {
    private lateinit var service: AlertService
    private lateinit var handler: RecordingNotificationHandler

    @BeforeTest
    fun setup() {
        handler = RecordingNotificationHandler()
        val dispatcher = NotificationDispatcher(listOf(handler))
        val client = SearchClient(restClient = FailingRestClient())
        service = AlertService(client = client, dispatcher = dispatcher)
    }

    @AfterTest
    fun tearDown() = runBlocking {
        service.stop()
    }

    @Test
    fun `rule failure sends failure notification`() = runBlocking {
        val configuration = alertConfiguration {
            notifications {
                console("success") {
                    message = "success"
                }
                console("failure") {
                    message = "failure: {{errorMessage}}"
                }
            }
            rules {
                rule("failing-rule") {
                    name = "Failing rule"
                    target("logs")
                    cron("* * * * *")
                    query {
                        query = matchAll()
                    }
                    notifications("success")
                    failureNotifications("failure")
                }
            }
        }

        service.start(configuration)

        val event = withTimeout(5.seconds) { handler.events.receive() }

        kotlin.test.assertEquals("Failing rule", event.context.ruleName)
        kotlin.test.assertEquals("FAILURE", event.variables[NotificationVariable.STATUS.key])
        kotlin.test.assertEquals("failure", event.definition.id)
        kotlin.test.assertEquals(
            com.jillesvangurp.ktsearch.RestException::class.qualifiedName,
            event.variables[NotificationVariable.ERROR_TYPE.key]
        )
        kotlin.test.assertEquals("EXECUTION", event.variables[NotificationVariable.FAILURE_PHASE.key])
        kotlin.test.assertEquals("1", event.variables[NotificationVariable.FAILURE_COUNT.key])
    }

    private class RecordingNotificationHandler : NotificationHandler {
        val events: Channel<NotificationEvent> = Channel(Channel.UNLIMITED)
        override val channel: NotificationChannel = NotificationChannel.CONSOLE

        override suspend fun send(
            definition: NotificationDefinition,
            variables: Map<String, String>,
            context: NotificationContext
        ) {
            events.send(NotificationEvent(definition, variables, context))
        }
    }

    private data class NotificationEvent(
        val definition: NotificationDefinition,
        val variables: Map<String, String>,
        val context: NotificationContext
    )

    private class FailingRestClient : RestClient {
        override suspend fun nextNode(): Node = Node("localhost", 9200)

        override fun close() = Unit

        override suspend fun doRequest(
            pathComponents: List<String>,
            httpMethod: com.jillesvangurp.ktsearch.HttpMethod,
            parameters: Map<String, Any>?,
            payload: String?,
            contentType: String,
            headers: Map<String, Any>?
        ): RestResponse {
            return RestResponse.Status5xx.InternalServerError(byteArrayOf()).also {
                throw com.jillesvangurp.ktsearch.RestException(it)
            }
        }
    }
}
