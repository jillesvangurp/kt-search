package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.CatRequestOptions as ClientCatRequestOptions
import com.jillesvangurp.ktsearch.CatBytes as ClientCatBytes
import com.jillesvangurp.ktsearch.CatFormat as ClientCatFormat
import com.jillesvangurp.ktsearch.CatTime as ClientCatTime
import com.jillesvangurp.ktsearch.catAliases
import com.jillesvangurp.ktsearch.catAllocation
import com.jillesvangurp.ktsearch.catCount
import com.jillesvangurp.ktsearch.catHealth
import com.jillesvangurp.ktsearch.catIndices
import com.jillesvangurp.ktsearch.catMaster
import com.jillesvangurp.ktsearch.catNodes
import com.jillesvangurp.ktsearch.catPendingTasks
import com.jillesvangurp.ktsearch.catRecovery
import com.jillesvangurp.ktsearch.catRepositories
import com.jillesvangurp.ktsearch.catShards
import com.jillesvangurp.ktsearch.catSnapshots
import com.jillesvangurp.ktsearch.catTasks
import com.jillesvangurp.ktsearch.catTemplates
import com.jillesvangurp.ktsearch.catThreadPool
import com.jillesvangurp.ktsearch.clusterHealth
import com.jillesvangurp.ktsearch.post
import com.jillesvangurp.ktsearch.root
import com.jillesvangurp.ktsearch.searchAfter
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Small service abstraction to keep command tests hermetic. */
interface CliService {
    suspend fun fetchStatus(connectionOptions: ConnectionOptions): StatusResult

    suspend fun dumpIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        writer: NdjsonGzipWriter,
    ): Long

    suspend fun searchIndexRaw(
        connectionOptions: ConnectionOptions,
        index: String,
        query: String?,
        data: String?,
        size: Int,
        offset: Int,
        fields: List<String>?,
        sort: String?,
        trackTotalHits: Boolean?,
        timeout: String?,
        routing: String?,
        preference: String?,
        allowPartialResults: Boolean?,
        profile: Boolean,
        explain: Boolean,
        terminateAfter: Int?,
        searchType: String?,
    ): String

    suspend fun cat(
        connectionOptions: ConnectionOptions,
        request: CatRequest,
    ): String
}

/** Default service implementation backed by [SearchClient]. */
class DefaultCliService : CliService {
    override suspend fun fetchStatus(connectionOptions: ConnectionOptions): StatusResult {
        val client = createClient(connectionOptions)
        try {
            val root = client.root()
            val health = client.clusterHealth()
            return StatusResult(
                clusterName = if (health.clusterName.isNotBlank()) {
                    health.clusterName
                } else {
                    root.clusterName
                },
                status = health.status,
                timedOut = health.timedOut,
            )
        } finally {
            client.close()
        }
    }

    override suspend fun dumpIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        writer: NdjsonGzipWriter,
    ): Long {
        val client = createClient(connectionOptions)
        return try {
            var lines = 0L
            val (_, flow) = client.searchAfter(index, keepAlive = 1.minutes) {
                resultSize = 500
            }
            flow.collect { hit ->
                val source = hit.source
                    ?: error("Hit ${hit.index}/${hit.id} has no _source")
                val line = client.json.encodeToString(
                    JsonObject.serializer(),
                    source,
                )
                writer.writeLine(line)
                lines++
            }
            lines
        } finally {
            client.close()
        }
    }

    override suspend fun searchIndexRaw(
        connectionOptions: ConnectionOptions,
        index: String,
        query: String?,
        data: String?,
        size: Int,
        offset: Int,
        fields: List<String>?,
        sort: String?,
        trackTotalHits: Boolean?,
        timeout: String?,
        routing: String?,
        preference: String?,
        allowPartialResults: Boolean?,
        profile: Boolean,
        explain: Boolean,
        terminateAfter: Int?,
        searchType: String?,
    ): String {
        val client = createClient(connectionOptions)
        return try {
            val body = when {
                !data.isNullOrBlank() -> withExtraProfile(data, profile)
                !query.isNullOrBlank() -> createQueryStringBody(query, profile)
                else -> null
            }
            val response = client.restClient.post {
                path(index, "_search")
                parameter("size", size)
                parameter("from", offset)
                parameter("sort", sort)
                parameter("track_total_hits", trackTotalHits)
                parameter("timeout", timeout)
                parameter("routing", routing)
                parameter("preference", preference)
                parameter(
                    "allow_partial_search_results",
                    allowPartialResults,
                )
                parameter("explain", explain)
                parameter("terminate_after", terminateAfter)
                parameter("search_type", searchType)
                parameter("_source_includes", fields?.joinToString(","))
                body?.let { rawBody(it) }
            }.getOrThrow()
            response.text
        } finally {
            client.close()
        }
    }

    override suspend fun cat(
        connectionOptions: ConnectionOptions,
        request: CatRequest,
    ): String {
        val client = createClient(connectionOptions)
        return try {
            val options = ClientCatRequestOptions(
                headers = request.columns,
                sort = request.sort,
                verbose = request.verbose,
                help = request.help,
                bytes = parseBytes(request.bytes),
                time = parseTime(request.time),
                format = ClientCatFormat.Json,
                local = request.local,
                extraParameters = request.extraParameters,
            )
            when (request.variant) {
                CatVariant.Aliases -> client.catAliases(request.target, options)
                CatVariant.Allocation -> client.catAllocation(request.target, options)
                CatVariant.Count -> client.catCount(request.target, options)
                CatVariant.Health -> client.catHealth(options)
                CatVariant.Indices -> client.catIndices(request.target, options)
                CatVariant.Master -> client.catMaster(options)
                CatVariant.Nodes -> client.catNodes(request.target, options)
                CatVariant.PendingTasks -> client.catPendingTasks(options)
                CatVariant.Recovery -> client.catRecovery(request.target, options)
                CatVariant.Repositories -> client.catRepositories(options)
                CatVariant.Shards -> client.catShards(request.target, options)
                CatVariant.Snapshots -> {
                    client.catSnapshots(request.target ?: "_all", options)
                }
                CatVariant.Tasks -> client.catTasks(options)
                CatVariant.Templates -> client.catTemplates(request.target, options)
                CatVariant.ThreadPool -> client.catThreadPool(request.target, options)
            }
        } finally {
            client.close()
        }
    }

    private fun createClient(connectionOptions: ConnectionOptions): SearchClient {
        return SearchClient(
            KtorRestClient(
                host = connectionOptions.host,
                port = connectionOptions.port,
                https = connectionOptions.https,
                user = connectionOptions.user,
                password = connectionOptions.password,
                logging = connectionOptions.logging,
            ),
        )
    }
}

private fun parseBytes(value: String?): ClientCatBytes? {
    return when (value?.lowercase()) {
        null -> null
        "b" -> ClientCatBytes.B
        "kb" -> ClientCatBytes.Kb
        "mb" -> ClientCatBytes.Mb
        "gb" -> ClientCatBytes.Gb
        "tb" -> ClientCatBytes.Tb
        "pb" -> ClientCatBytes.Pb
        else -> null
    }
}

private fun parseTime(value: String?): ClientCatTime? {
    return when (value?.lowercase()) {
        null -> null
        "d" -> ClientCatTime.D
        "h" -> ClientCatTime.H
        "m" -> ClientCatTime.M
        "s" -> ClientCatTime.S
        "ms" -> ClientCatTime.Ms
        "micros" -> ClientCatTime.Micros
        "nanos" -> ClientCatTime.Nanos
        else -> null
    }
}

private fun createQueryStringBody(query: String, profile: Boolean): String {
    val body = buildJsonObject {
        put("query", buildJsonObject {
            put("query_string", buildJsonObject {
                put("query", JsonPrimitive(query))
            })
        })
        if (profile) {
            put("profile", JsonPrimitive(true))
        }
    }
    return body.toString()
}

private fun withExtraProfile(data: String, profile: Boolean): String {
    if (!profile) {
        return data
    }
    return try {
        val parsed = Json.Default.decodeFromString(JsonObject.serializer(), data)
        buildJsonObject {
            parsed.forEach { (k, v) -> put(k, v) }
            put("profile", JsonPrimitive(true))
        }.toString()
    } catch (_: Exception) {
        data
    }
}
