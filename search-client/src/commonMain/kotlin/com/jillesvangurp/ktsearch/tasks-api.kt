@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

suspend fun SearchClient.getTask(id: String?, waitForCompletion: Boolean = false, timeout: Duration = 1.minutes): JsonObject {
    return restClient.get {
        path("_tasks",id)
        if(waitForCompletion) {
            parameter("wait_for_completion",true)
            parameter("timeout","${timeout.inWholeSeconds}s")
        }
    }.parse(JsonObject.serializer())
}

suspend fun SearchClient.cancelTask(id: String,waitForCompletion: Boolean = false, timeout: Duration = 1.minutes): JsonObject {
    return restClient.post {
        path("_tasks",id,"_cancel")
        if(waitForCompletion) {
            parameter("wait_for_completion",true)
            parameter("timeout","${timeout.inWholeSeconds}s")
        }
    }.parse(JsonObject.serializer())
}


/**
 * Polls the given task id to check if the task is completed. Waits for [timeout] and polls every [interval] to check if the task is completed.
 *
 * Returns the task response object with an embedded response for the task if completed, null if the task does not exist.
 *
 * Throws an exception if the task does not complete before the timeout or if Elasticsearch returns an error.
 */
suspend fun SearchClient.awaitTaskCompleted(id: TaskId, timeout: Duration, interval: Duration = 5.seconds): JsonObject? {
    return awaitTaskCompleted(id.value,timeout,interval)
}

/**
 * Polls the given task id to check if the task is completed. Waits for [timeout] and polls every [interval] to check if the task is completed.
 *
 * Returns the task response object with an embedded response for the task if completed, null if the task does not exist.
 *
 * Throws an exception if the task does not complete before the timeout or if Elasticsearch returns an error.
 */
suspend fun SearchClient.awaitTaskCompleted(id: String, timeout: Duration, interval: Duration = 5.seconds): JsonObject? {
    return withTimeout(timeout) {
        var resp = getTask(id)
        try {
            while(resp["completed"]?.jsonPrimitive?.content != "true") {
                delay(interval)
                resp = getTask(id)
            }
            resp
        } catch (e: RestException) {
            if(e.status == 404) {
                // a 404 would indicate the task no longer exists and can be assumed to have completed
                null
            } else {
                throw e
            }
        }
    }
}