package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant.ES8
import com.jillesvangurp.searchdsls.VariantRestriction
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    val failures: List<String>
)

@Serializable
data class ReindexRetries(val bulk: Int, val search: Int)

@Serializable
data class ReindexBody(val source: ReindexSourceBody, val dest: ReindexDestinationBody)

@Serializable
data class ReindexSourceBody(val index: String)
@Serializable
data class ReindexDestinationBody(val index: String)

@VariantRestriction(ES8)
suspend fun SearchClient.reindex(json: Json = DEFAULT_JSON, reindexBody: ReindexBody): ReindexResponse = restClient.post {
    path("_reindex")
    body = json.encodeToString(ReindexBody.serializer(), reindexBody)
}.parse(ReindexResponse.serializer())