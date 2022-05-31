package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration

@Serializable
data class BulkResponse(
    val took: Long,
    val errors: Boolean,
    val items: List<JsonObject>
) {
    val itemDetails: List<Pair<OperationType, ItemDetails>> by lazy {
        items.map { obj ->
            when {
                obj.containsKey("create") -> {
                    val details = obj["create"]!!.jsonObject
                    OperationType.Create to details
                }
                obj.containsKey("index") -> {
                    val details = obj["index"]!!.jsonObject
                    OperationType.Index to details
                }
                obj.containsKey("update") -> {
                    val details = obj["update"]!!.jsonObject
                    OperationType.Update to details
                }
                obj.containsKey("delete") -> {
                    val details = obj["delete"]!!.jsonObject
                    OperationType.Delete to details
                }
                else -> {
                    error("unexpected operation response: $obj")
                }
            }.let { (type, details) ->
                type to DEFAULT_JSON.decodeFromJsonElement(ItemDetails.serializer(), details)
            }
        }
    }

    @Serializable
    data class ItemDetails(
        @SerialName("_index")
        val index: String,
        @SerialName("_type")
        val type: String,
        @SerialName("_id")
        val id: String,
        @SerialName("_version")
        val version: Long,
        val result: String,
        @SerialName("_shards")
        val shards: Shards,
        @SerialName("_seq_no")
        val seqNo: Long,
        @SerialName("_primary_term")
        val primaryTerm: Long,
        val status: Int
    )
}

interface BulkItemCallBack {
    fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails)

    fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails)
}

class BulkException(bulkResponse: BulkResponse) : Exception(
    "Bulk request completed with errors item statuses: [${
        bulkResponse.itemDetails.map { (_, details) -> details.status }.distinct().joinToString(",")
    }]"
)

/**
 * Create using SearchClient.bulk()
 */
class BulkSession internal constructor(
    val searchClient: SearchClient,
    val failOnFirstError: Boolean = false,
    val callBack: BulkItemCallBack? = null,
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

    suspend fun index(source: String, index: String? = null, id: String? = null, requireAlias: Boolean? = null) {
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

    suspend fun delete(id: String, index: String? = null, requireAlias: Boolean? = null) {
        val opDsl = JsonDsl().apply {
            this["delete"] = JsonDsl().apply {
                this["_id"] = id
                index?.let {
                    this["_index"] = index
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

            val response = searchClient.restClient.post {
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

                rawBody(ops.flatMap { listOfNotNull(it.first, it.second) }.joinToString("\n") + "\n")
            }.parse(BulkResponse.serializer(), searchClient.json)

            if (callBack != null) {
                response.itemDetails.forEach { (type, details) ->
                    if (details.status < 300) {
                        callBack.itemOk(type, details)
                    } else {
                        callBack.itemFailed(type, details)
                    }
                }
            }
            if (response.errors && failOnFirstError) {
                throw BulkException(response)
            }
        }
    }
}

/**
 * Creates a bulk session that allows you to index, create, or delete items.
 *
 * The BulkSession takes care of firing off bulk requests with the specified bulkSize
 * so you don't have to manually construct bulk requests.
 *
 * You can use the callback to deal with individual item responses. This is
 * useful for dealing with e.g. version conflicts or other non-fatal item problems.
 * Also, you can get to things like seq_no and primary_term for optimistic locking.
 *
 * If you are expecting all items to succeed, you should set failFast to true.
 */
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
    failOnFirstError: Boolean = false,
    callBack: BulkItemCallBack? = null,
    block: suspend BulkSession.() -> Unit
) {
    val session = BulkSession(
        searchClient = this,
        failOnFirstError = failOnFirstError,
        callBack = callBack,
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

