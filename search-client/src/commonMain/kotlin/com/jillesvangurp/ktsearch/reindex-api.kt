package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant.ES7
import com.jillesvangurp.searchdsls.SearchEngineVariant.ES8
import com.jillesvangurp.searchdsls.SearchEngineVariant.ES9
import com.jillesvangurp.searchdsls.VariantRestriction
import com.jillesvangurp.searchdsls.querydsl.ReindexDSL
import com.jillesvangurp.serializationext.DEFAULT_JSON
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

@Serializable
data class ReindexResponse(
    val took: Int,
    @SerialName("timed_out")
    val timedOut: Boolean,
    val total: Int,
    val updated: Int,
    val created: Int,
    val deleted: Int,
    val batches: Int,
    @SerialName("version_conflicts")
    val versionConflicts: Int,
    val noops: Int,
    val retries: ReindexRetries,
    @SerialName("throttled_millis")
    val throttledMillis: Int,
    @SerialName("requests_per_second")
    val requestsPerSecond: Double,
    @SerialName("throttled_until_millis")
    val throttledUntilMillis: Int,
    val failures: List<String>,
)

@Serializable
data class ReindexRetries(val bulk: Int, val search: Int)

/**
 * Reindex and waits for completion. Returns a reindex response indicating what was reindexed.
 *
 * Use [reindexAsync] if you want to process the response in the background and use the tasks API to follow progress.
 */
@VariantRestriction(ES7, ES8,ES9)
suspend fun SearchClient.reindex(
    refresh: Boolean? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    requestsPerSecond: Int? = null,
    requireAlias: Boolean? = null,
    scroll: Duration? = null,
    slices: Int? = null,
    maxDocs: Int? = null,
    block: ReindexDSL.() -> Unit,
): ReindexResponse = reindexGeneric(
    refresh,
    timeout,
    waitForActiveShards,
    true,
    requestsPerSecond,
    requireAlias,
    scroll,
    slices,
    maxDocs,
    block
).parse(ReindexResponse.serializer())

/**
 * Reindexes using a background task in ES. Returns a TaskId response with an id that you can use with the task api.
 */
@VariantRestriction(ES7, ES8, ES9)
suspend fun SearchClient.reindexAsync(
    refresh: Boolean? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    requestsPerSecond: Int? = null,
    requireAlias: Boolean? = null,
    scroll: Duration? = null,
    slices: Int? = null,
    maxDocs: Int? = null,
    block: ReindexDSL.() -> Unit,
): TaskId = reindexGeneric(
    refresh,
    timeout,
    waitForActiveShards,
    false,
    requestsPerSecond,
    requireAlias,
    scroll,
    slices,
    maxDocs,
    block
).parse(TaskResponse.serializer()).toTaskId()

/**
 * Reindexes using a background task in ES and polls for that task to complete every [pollInterval] for a maximum of [timeout].
 * Note: the same timeout is passed on to the reindex task
 *
 * Returns a nullable [ReindexResponse]
 */
@VariantRestriction(ES7, ES8, ES9)
suspend fun SearchClient.reindexAndAwaitTask(
    refresh: Boolean? = null,
    timeout: Duration = 20.minutes,
    waitForActiveShards: String? = null,
    requestsPerSecond: Int? = null,
    requireAlias: Boolean? = null,
    scroll: Duration? = null,
    slices: Int? = null,
    maxDocs: Int? = null,
    pollInterval: Duration = 5.seconds,
    block: ReindexDSL.() -> Unit,
): ReindexResponse? {
    val taskId = reindexGeneric(
        refresh,
        timeout,
        waitForActiveShards,
        false,
        requestsPerSecond,
        requireAlias,
        scroll,
        slices,
        maxDocs,
        block
    ).parse(TaskResponse.serializer()).toTaskId()
    val taskResp = awaitTaskCompleted(taskId,timeout, interval = pollInterval)
    return taskResp?.get("response")?.let {
        // extract the reindex response
        DEFAULT_JSON.decodeFromJsonElement(ReindexResponse.serializer(), it)
    }
}


private suspend fun SearchClient.reindexGeneric(
    refresh: Boolean? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    waitForCompletion: Boolean? = null,
    requestsPerSecond: Int? = null,
    requireAlias: Boolean? = null,
    scroll: Duration? = null,
    slices: Int? = null,
    maxDocs: Int? = null,
    block: ReindexDSL.() -> Unit,
): Result<RestResponse.Status2XX> {
    val reindexDSL = ReindexDSL()
    block(reindexDSL)

    return restClient.post {
        path("_reindex")
        parameter("refresh", refresh)
        parameter("timeout", timeout)
        parameter("wait_for_active_shards", waitForActiveShards)
        parameter("wait_for_completion", waitForCompletion)
        parameter("requests_per_second", requestsPerSecond)
        parameter("require_alias", requireAlias)
        parameter("scroll", scroll)
        parameter("slices", slices)
        parameter("max_docs", maxDocs)
        body = reindexDSL.toString()
    }
}

@Serializable
data class TaskResponse(val task: String) {
    fun toTaskId() = TaskId(task)
}

data class TaskId(val value: String)