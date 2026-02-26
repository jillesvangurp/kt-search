package com.jillesvangurp.ktsearch.cli

import com.github.ajalt.clikt.core.CliktError
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.ClusterHealthResponse
import com.jillesvangurp.ktsearch.Refresh
import com.jillesvangurp.ktsearch.RestException
import com.jillesvangurp.ktsearch.RestResponse
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.TaskResponse
import com.jillesvangurp.ktsearch.clusterStats
import com.jillesvangurp.ktsearch.clusterHealth
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
import com.jillesvangurp.ktsearch.getTask
import com.jillesvangurp.ktsearch.post
import com.jillesvangurp.ktsearch.put
import com.jillesvangurp.ktsearch.searchAfter
import com.jillesvangurp.ktsearch.nodesStats
import com.jillesvangurp.ktsearch.cli.command.tasks.TaskProgress
import com.jillesvangurp.ktsearch.cli.command.cluster.ClusterTopApiSnapshot
import com.jillesvangurp.ktsearch.withTemporaryIndexingSettings
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Clock
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Small service abstraction to keep command tests hermetic. */
interface CliService {
    suspend fun fetchRootInfo(connectionOptions: ConnectionOptions): String

    suspend fun fetchClusterHealth(connectionOptions: ConnectionOptions): String

    suspend fun fetchClusterTopSnapshot(
        connectionOptions: ConnectionOptions,
    ): ClusterTopApiSnapshot

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
        disableRefreshInterval: Boolean = false,
        setReplicasToZero: Boolean = false,
    ): Long

    suspend fun reindex(
        connectionOptions: ConnectionOptions,
        body: String,
        waitForCompletion: Boolean = false,
        disableRefreshInterval: Boolean = false,
        setReplicasToZero: Boolean = false,
        onTaskProgress: ((TaskProgress) -> Unit)? = null,
    ): String

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
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
                client.restClient.get { }.getOrThrow().text
            } finally {
                client.close()
            }
        }
    }

    override suspend fun fetchClusterHealth(connectionOptions: ConnectionOptions): String {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
                client.restClient.get {
                    path("_cluster", "health")
                }.getOrThrow().text
            } finally {
                client.close()
            }
        }
    }

    override suspend fun fetchClusterTopSnapshot(
        connectionOptions: ConnectionOptions,
    ): ClusterTopApiSnapshot {
        val client = createClient(connectionOptions)
        return try {
            val errors = mutableListOf<String>()
            val clusterStats = runCatching {
                client.clusterStats(
                    filterPath = listOf(
                        "cluster_name",
                        "status",
                        "indices.docs.count",
                        "indices.shards.total",
                        "indices.store.size_in_bytes",
                        "indices.segments.count",
                        "indices.segments.memory_in_bytes",
                        "nodes.count.total",
                    ),
                )
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "cluster stats request failed",
                )
            }.getOrNull()

            val clusterHealth = runCatching {
                client.clusterHealth(
                    extraParameters = mapOf(
                        "filter_path" to listOf(
                            "cluster_name",
                            "status",
                            "timed_out",
                            "number_of_nodes",
                            "active_shards",
                            "relocating_shards",
                            "initializing_shards",
                            "unassigned_shards",
                        ).joinToString(","),
                    ),
                )
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "cluster health request failed",
                )
            }.getOrNull()

            val nodesStats = runCatching {
                client.nodesStats(
                    metrics = listOf(
                        "os",
                        "process",
                        "jvm",
                        "fs",
                        "indices",
                        "thread_pool",
                    ),
                    filterPath = listOf(
                        "cluster_name",
                        "nodes.*.name",
                        "nodes.*.ip",
                        "nodes.*.host",
                        "nodes.*.roles",
                        "nodes.*.os.cpu.percent",
                        "nodes.*.process.cpu.percent",
                        "nodes.*.jvm.mem.heap_used_in_bytes",
                        "nodes.*.jvm.mem.heap_max_in_bytes",
                        "nodes.*.jvm.mem.heap_used_percent",
                        "nodes.*.fs.total.total_in_bytes",
                        "nodes.*.fs.total.available_in_bytes",
                        "nodes.*.indices.docs.count",
                        "nodes.*.indices.store.size_in_bytes",
                        "nodes.*.indices.segments.count",
                        "nodes.*.indices.segments.memory_in_bytes",
                        "nodes.*.indices.indexing.index_total",
                        "nodes.*.indices.search.query_total",
                        "nodes.*.thread_pool.*.active",
                        "nodes.*.thread_pool.*.queue",
                        "nodes.*.thread_pool.*.rejected",
                    ),
                )
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "nodes stats request failed",
                )
            }.getOrNull()

            val indicesStatsRaw = runCatching {
                client.restClient.get {
                    path("_stats")
                    parameter("level", "indices")
                    parameter(
                        "filter_path",
                        listOf(
                            "indices.*.total.docs.count",
                            "indices.*.total.store.size_in_bytes",
                            "indices.*.total.segments.memory_in_bytes",
                            "indices.*.total.indexing.index_total",
                            "indices.*.total.search.query_total",
                        ).joinToString(","),
                    )
                }.getOrThrow().text
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "indices stats request failed",
                )
            }.getOrNull()

            val threadPoolCatRaw = runCatching {
                client.restClient.get {
                    path("_cat", "thread_pool")
                    parameter("format", "json")
                    parameter("h", "node_name,name,active,queue,rejected")
                    parameter("s", "queue:desc,rejected:desc")
                }.getOrThrow().text
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "thread pool request failed",
                )
            }.getOrNull()

            val allocationCatRaw = runCatching {
                client.restClient.get {
                    path("_cat", "allocation")
                    parameter("format", "json")
                    parameter("h", "node,disk.percent,disk.avail,disk.total,shards")
                }.getOrThrow().text
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "allocation request failed",
                )
            }.getOrNull()

            val clusterSettingsRaw = runCatching {
                client.restClient.get {
                    path("_cluster", "settings")
                    parameter("include_defaults", true)
                    parameter("flat_settings", true)
                }.getOrThrow().text
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "cluster settings request failed",
                )
            }.getOrNull()

            val tasksRaw = runCatching {
                client.restClient.get {
                    path("_tasks")
                    parameter("detailed", true)
                    parameter("actions", "indices:*search*,*reindex*,*byquery*")
                    parameter(
                        "filter_path",
                        "nodes.*.name,nodes.*.host,nodes.*.tasks.*.action," +
                            "nodes.*.tasks.*.description," +
                            "nodes.*.tasks.*.running_time_in_nanos",
                    )
                }.getOrThrow().text
            }.onFailure { error ->
                errors.add(
                    mapCliException(error, connectionOptions).message
                        ?: "tasks request failed",
                )
            }.getOrNull()

            ClusterTopApiSnapshot(
                clusterStats = clusterStats,
                clusterHealth = clusterHealth,
                nodesStats = nodesStats,
                indicesStatsRaw = indicesStatsRaw,
                threadPoolCatRaw = threadPoolCatRaw,
                allocationCatRaw = allocationCatRaw,
                clusterSettingsRaw = clusterSettingsRaw,
                tasksRaw = tasksRaw,
                errors = errors,
                fetchedAt = Clock.System.now(),
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
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
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
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
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
    }

    override suspend fun cat(
        connectionOptions: ConnectionOptions,
        request: CatRequest,
    ): String {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
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
                    CatVariant.ThreadPool -> {
                        client.catThreadPool(request.target, options)
                    }
                }
            } finally {
                client.close()
            }
        }
    }

    override suspend fun apiRequest(
        connectionOptions: ConnectionOptions,
        method: ApiMethod,
        path: List<String>,
        parameters: Map<String, String>?,
        data: String?,
    ): String {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
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
        disableRefreshInterval: Boolean,
        setReplicasToZero: Boolean,
    ): Long {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
                val indexExists = client.exists(index)
                if (recreate && indexExists) {
                    client.deleteIndex(index, ignoreUnavailable = true)
                }
                if (recreate || (createIfMissing && !indexExists)) {
                    client.createIndex(index)
                }

                suspend fun runRestore(): Long {
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
                                        .decodeFromString(
                                            JsonObject.serializer(),
                                            trimmed,
                                        ).get(key)
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
                        return lines
                    } finally {
                        session.close()
                    }
                }
                if (disableRefreshInterval || setReplicasToZero) {
                    client.withTemporaryIndexingSettings(
                        target = index,
                        disableRefreshInterval = disableRefreshInterval,
                        setReplicasToZero = setReplicasToZero,
                    ) {
                        runRestore()
                    }
                } else {
                    runRestore()
                }
            } finally {
                client.close()
            }
        }
    }

    override suspend fun reindex(
        connectionOptions: ConnectionOptions,
        body: String,
        waitForCompletion: Boolean,
        disableRefreshInterval: Boolean,
        setReplicasToZero: Boolean,
        onTaskProgress: ((TaskProgress) -> Unit)?,
    ): String {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
                val destinationIndex = Json.parseToJsonElement(body)
                    .jsonObject["dest"]?.jsonObject
                    ?.get("index")?.jsonPrimitive?.contentOrNull

                suspend fun runReindex() = client.restClient.post {
                    path("_reindex")
                    parameter("wait_for_completion", waitForCompletion)
                    rawBody(body)
                }.getOrThrow().text

                if (disableRefreshInterval || setReplicasToZero) {
                    val destination = requireNotNull(destinationIndex) {
                        "dest.index is required when --disable-refresh-interval " +
                            "or --set-replicas-zero is enabled"
                    }
                    client.withTemporaryIndexingSettings(
                        target = destination,
                        disableRefreshInterval = disableRefreshInterval,
                        setReplicasToZero = setReplicasToZero,
                    ) {
                        val response = runReindex()
                        if (!waitForCompletion) {
                            val taskId = Json.Default.decodeFromString(
                                TaskResponse.serializer(),
                                response,
                            ).task
                            pollReindexTaskUntilCompleted(
                                client = client,
                                taskId = taskId,
                                timeout = 12.hours,
                                onTaskProgress = onTaskProgress,
                            )
                        }
                        response
                    }
                } else {
                    val response = runReindex()
                    if (!waitForCompletion && onTaskProgress != null) {
                        val taskId = Json.Default.decodeFromString(
                            TaskResponse.serializer(),
                            response,
                        ).task
                        pollReindexTaskUntilCompleted(
                            client = client,
                            taskId = taskId,
                            timeout = 12.hours,
                            onTaskProgress = onTaskProgress,
                        )
                    }
                    response
                }
            } finally {
                client.close()
            }
        }
    }

    override suspend fun indexExists(
        connectionOptions: ConnectionOptions,
        index: String,
    ): Boolean {
        return runWithCliErrorMapping(connectionOptions) {
            val client = createClient(connectionOptions)
            try {
                client.exists(index)
            } finally {
                client.close()
            }
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
                elasticApiKey = connectionOptions.elasticApiKey,
                logging = connectionOptions.logging,
            ),
        )
    }
}

private suspend fun pollReindexTaskUntilCompleted(
    client: SearchClient,
    taskId: String,
    timeout: kotlin.time.Duration,
    onTaskProgress: ((TaskProgress) -> Unit)?,
) {
    val started = kotlin.time.Clock.System.now()
    while (true) {
        val status = client.getTask(taskId)
        val completed = status["completed"]?.jsonPrimitive?.content == "true"
        status["task"]?.jsonObject?.get("status")?.jsonObject?.let { s ->
            onTaskProgress?.invoke(
                TaskProgress(
                    taskId = taskId,
                    total = s["total"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                    processed =
                        (s["created"]?.jsonPrimitive?.contentOrNull
                            ?.toLongOrNull() ?: 0L) +
                            (s["updated"]?.jsonPrimitive?.contentOrNull
                                ?.toLongOrNull() ?: 0L) +
                            (s["deleted"]?.jsonPrimitive?.contentOrNull
                                ?.toLongOrNull() ?: 0L),
                    batches = s["batches"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                ),
            )
        }
        if (completed) {
            return
        }
        if (kotlin.time.Clock.System.now() - started > timeout) {
            error("Timed out waiting for reindex task $taskId")
        }
        kotlinx.coroutines.delay(5.seconds)
    }
}

private suspend fun <T> runWithCliErrorMapping(
    connectionOptions: ConnectionOptions,
    block: suspend () -> T,
): T {
    return try {
        block()
    } catch (e: Throwable) {
        throw mapCliException(e, connectionOptions)
    }
}

internal fun mapCliException(
    error: Throwable,
    connectionOptions: ConnectionOptions,
): Throwable {
    if (error is CliktError || error is Error) {
        return error
    }
    return when (error) {
        is RestException -> CliRequestError(mapRestError(error, connectionOptions))
        else -> if (looksLikeNetworkError(error)) {
            CliRequestError(
                "Unable to connect to ${connectionOptions.baseUrl()}. " +
                    "Check --host/--port/--http/--https and that the " +
                    "cluster is reachable.",
            )
        } else {
            error
        }
    }
}

private fun mapRestError(
    error: RestException,
    connectionOptions: ConnectionOptions,
): String {
    val status = error.status
    val path = (error.response as? RestResponse.Status4XX)?.path
    return when (status) {
        400 -> "Request failed with HTTP 400 (Bad Request) for " +
            "${path ?: connectionOptions.baseUrl()}. Verify request flags " +
            "and payload."
        404 -> "Request failed with HTTP 404 (Not Found) for " +
            "${path ?: connectionOptions.baseUrl()}. Verify resource names " +
            "and endpoint path."
        else -> "Request failed with HTTP $status for " +
            "${path ?: connectionOptions.baseUrl()}."
    }
}

private fun looksLikeNetworkError(error: Throwable): Boolean {
    var current: Throwable? = error
    while (current != null) {
        val typeName = current::class.simpleName.orEmpty()
        if (
            typeName.contains("Connect", ignoreCase = true) ||
            typeName.contains("Socket", ignoreCase = true) ||
            typeName.contains("Timeout", ignoreCase = true) ||
            typeName.contains("Host", ignoreCase = true) ||
            typeName.contains("Network", ignoreCase = true) ||
            typeName.contains("Address", ignoreCase = true) ||
            typeName.contains("IO", ignoreCase = true)
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

private fun ConnectionOptions.baseUrl(): String {
    val scheme = if (https) "https" else "http"
    return "$scheme://$host:$port"
}

private class CliRequestError(
    message: String,
) : CliktError(message = message, statusCode = 1, printError = true)

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
