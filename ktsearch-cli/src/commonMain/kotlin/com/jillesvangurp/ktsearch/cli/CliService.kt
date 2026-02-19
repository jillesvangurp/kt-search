package com.jillesvangurp.ktsearch.cli

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulkSession
import com.jillesvangurp.ktsearch.createIndex
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
import com.jillesvangurp.ktsearch.delete
import com.jillesvangurp.ktsearch.deleteIndex
import com.jillesvangurp.ktsearch.exists
import com.jillesvangurp.ktsearch.get
import com.jillesvangurp.ktsearch.post
import com.jillesvangurp.ktsearch.put
import com.jillesvangurp.ktsearch.searchAfter
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/** Small service abstraction to keep command tests hermetic. */
interface CliService {
    suspend fun fetchRootInfo(connectionOptions: ConnectionOptions): String

    suspend fun fetchClusterHealth(connectionOptions: ConnectionOptions): String

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

    suspend fun apiRequest(
        connectionOptions: ConnectionOptions,
        method: ApiMethod,
        path: List<String>,
        parameters: Map<String, String>? = null,
        data: String? = null,
    ): String

    suspend fun restoreIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        reader: NdjsonGzipReader,
        bulkSize: Int = 500,
        createIfMissing: Boolean = true,
        recreate: Boolean = false,
        refresh: String = "wait_for",
        pipeline: String? = null,
        routing: String? = null,
        idField: String? = null,
    ): Long

    suspend fun indexExists(
        connectionOptions: ConnectionOptions,
        index: String,
    ): Boolean
}

enum class ApiMethod {
    Get,
    Post,
    Put,
    Delete,
}

/** Default service implementation backed by [SearchClient]. */
class DefaultCliService : CliService {
    override suspend fun fetchRootInfo(connectionOptions: ConnectionOptions): String {
        val client = createClient(connectionOptions)
        return try {
            client.restClient.get { }.getOrThrow().text
        } finally {
            client.close()
        }
    }

    override suspend fun fetchClusterHealth(connectionOptions: ConnectionOptions): String {
        val client = createClient(connectionOptions)
        return try {
            client.restClient.get {
                path("_cluster", "health")
            }.getOrThrow().text
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

    override suspend fun apiRequest(
        connectionOptions: ConnectionOptions,
        method: ApiMethod,
        path: List<String>,
        parameters: Map<String, String>?,
        data: String?,
    ): String {
        val client = createClient(connectionOptions)
        return try {
            val response = when (method) {
                ApiMethod.Get -> client.restClient.get {
                    path(*path.toTypedArray())
                    parameters(parameters)
                }.getOrThrow()
                ApiMethod.Post -> client.restClient.post {
                    path(*path.toTypedArray())
                    parameters(parameters)
                    data?.let { rawBody(it) }
                }.getOrThrow()
                ApiMethod.Put -> client.restClient.put {
                    path(*path.toTypedArray())
                    parameters(parameters)
                    data?.let { rawBody(it) }
                }.getOrThrow()
                ApiMethod.Delete -> client.restClient.delete {
                    path(*path.toTypedArray())
                    parameters(parameters)
                    data?.let { rawBody(it) }
                }.getOrThrow()
            }
            response.text
        } finally {
            client.close()
        }
    }

    override suspend fun restoreIndex(
        connectionOptions: ConnectionOptions,
        index: String,
        reader: NdjsonGzipReader,
        bulkSize: Int,
        createIfMissing: Boolean,
        recreate: Boolean,
        refresh: String,
        pipeline: String?,
        routing: String?,
        idField: String?,
    ): Long {
        val client = createClient(connectionOptions)
        return try {
            val indexExists = client.exists(index)
            if (recreate && indexExists) {
                client.deleteIndex(index, ignoreUnavailable = true)
            }
            if (recreate || (createIfMissing && !indexExists)) {
                client.createIndex(index)
            }

            val session = client.bulkSession(
                target = index,
                bulkSize = bulkSize,
                refresh = parseRefresh(refresh),
                pipeline = pipeline,
                routing = routing,
                failOnFirstError = true,
            )
            try {
                var lines = 0L
                while (true) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        continue
                    }
                    val id = idField?.let { key ->
                        runCatching {
                            Json.Default
                                .decodeFromString(JsonObject.serializer(), trimmed)
                                .get(key)
                                ?.let { value ->
                                    if (value is JsonPrimitive) {
                                        value.content
                                    } else {
                                        value.toString()
                                    }
                                }
                        }.getOrNull()
                    }
                    session.index(source = trimmed, id = id)
                    lines++
                }
                session.flush()
                lines
            } finally {
                session.close()
            }
        } finally {
            client.close()
        }
    }

    override suspend fun indexExists(
        connectionOptions: ConnectionOptions,
        index: String,
    ): Boolean {
        val client = createClient(connectionOptions)
        return try {
            client.exists(index)
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

private fun parseRefresh(value: String): Refresh {
    return when (value.lowercase()) {
        "wait_for", "waitfor" -> Refresh.WaitFor
        "true" -> Refresh.True
        "false" -> Refresh.False
        else -> Refresh.WaitFor
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
