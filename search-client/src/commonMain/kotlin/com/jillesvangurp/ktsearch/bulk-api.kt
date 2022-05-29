package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import kotlin.time.Duration


class BulkSession internal constructor(
    val searchClient: SearchClient,
    val bulkSize: Int,
    val target: String?,
    val pipeline: String? = null,
    val refresh: Refresh? = null,
    val routing: String? = null,
    val timeout: Duration? = null,
    val waitForActiveShards: String? = null,
    val requireAlias: Boolean? = null,
    val source: String? = null,
    val sourceExcludes: String? = null,
    val sourceIncludes: String? = null,
) {
    private val operations: MutableList<Pair<String, String?>> = mutableListOf()

    suspend fun create(source: String, index: String? = null, id: String? = null, requireAlias: Boolean? = null) {
        val opDsl = JsonDsl().apply {
            this["create"] = JsonDsl().apply {
                index?.let {
                    this["_index"] = index
                }
                id?.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
            }
        }
        operation(opDsl.json(), source)
    }

    suspend fun index(source: String, index: String?, id: String? = null, requireAlias: Boolean? = null) {
        val opDsl = JsonDsl().apply {
            this["index"] = JsonDsl().apply {
                index?.let {
                    this["_index"] = index
                }
                id?.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
            }
        }
        operation(opDsl.json(), source)
    }

    suspend fun delete(index: String?, id: String? = null, requireAlias: Boolean? = null) {
        val opDsl = JsonDsl().apply {
            this["delete"] = JsonDsl().apply {
                index?.let {
                    this["_index"] = index
                }
                id?.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
            }
        }
        operation(opDsl.json())
    }

    suspend fun operation(operation: String, source: String? = null) {
        operations.add(operation to source)
        if (operations.size > bulkSize) {
            flush()
        }
    }

    suspend fun flush() {
        if (operations.isNotEmpty()) {
            val ops = mutableListOf<Pair<String, String?>>()
            ops.addAll(operations)
            operations.clear()

            searchClient.restClient.post {
                if (target.isNullOrBlank()) {
                    path("_bulk")
                } else {
                    path(target, "_bulk")
                }

                parameter("pipeline", pipeline)
                parameter("refresh", refresh)
                parameter("routing", routing)
                parameter("timeout", timeout)
                parameter("wait_for_active_shards", waitForActiveShards)
                parameter("require_alias", requireAlias)
                parameter("source", source)
                parameter("source_excludes", sourceExcludes)
                parameter("source_includes", sourceIncludes)

                rawBody(ops.flatMap {  listOfNotNull(it.first,it.second) }.joinToString("\n") + "\n")
            }
        }
    }
}

suspend fun SearchClient.bulk(
    bulkSize: Int = 100,
    target: String? = null,
    pipeline: String? = null,
    refresh: Refresh? = null,
    routing: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    requireAlias: Boolean? = null,
    source: String? = null,
    sourceExcludes: String? = null,
    sourceIncludes: String? = null,
    block: suspend BulkSession.() -> Unit
) {
    val session = BulkSession(
        searchClient = this,
        bulkSize = bulkSize,
        pipeline = pipeline,
        refresh = refresh,
        routing = routing,
        timeout = timeout,
        waitForActiveShards = waitForActiveShards,
        requireAlias = requireAlias,
        source = source,
        sourceExcludes = sourceExcludes,
        sourceIncludes = sourceIncludes,
        target = target
    )
    block.invoke(session)
    session.flush()
}

