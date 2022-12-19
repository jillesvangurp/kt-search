@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

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