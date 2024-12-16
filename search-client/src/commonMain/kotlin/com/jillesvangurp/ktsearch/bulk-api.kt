package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.querydsl.Script
import io.ktor.utils.io.core.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
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
    data class ItemError(
        val type: String? = null,
        val reason: String? = null,
        @SerialName("index_uuid")
        val indexUuid: String? = null,
        val shard: String? = null,
        val index: String? = null,
    )

    @Serializable
    data class ItemDetails(
        @SerialName("_index")
        val index: String,
        @SerialName("_type")
        val type: String?,
        @SerialName("_id")
        val id: String,
        @SerialName("_version")
        val version: Long?,
        val result: String?,
        @SerialName("_shards")
        val shards: Shards?,
        @SerialName("_seq_no")
        val seqNo: Long?,
        @SerialName("_primary_term")
        val primaryTerm: Long?,
        val status: Int,
        val error: ItemError? = null,
        @SerialName("_source")
        val source: JsonObject? = null,
    )
}

/**
 * Use this to deal with item responses and failures; or bulk request errors. By default, the callback is null.
 */
interface BulkItemCallBack {
    /**
     * Called when the bulk response marks the item with a non successful status.
     */
    fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails)

    /**
     * Called to confirm an item was processes successfully with details about the item (e.g. primary_term, seq_no, id, etc.)
     */
    fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails)

    /**
     * Called when elasticsearch responds with an error. By default, this is considered fatal and the bulk session
     * closes and is no longer usable and an exception will be thrown.
     * You may choose to keep it open by setting `closeOnRequestError` to false when you create the
     * `BulkSession` to e.g. implement a retry strategy or simply drop the failed bulk requests.
     *
     * Note, exceptions may happen  for all sorts of reasons, including the cluster
     * being unreachable, temporarily unavailable, or configuration errors.
     *
     * @param e the exception that was thrown. If of type `RestException`, you may be able to implement some recover procedure
     * @param ops the list of operations and their payload (or null for e.g. delete)
     */
    fun bulkRequestFailed(e: Exception, ops: List<Pair<String, String?>>)
}

class BulkException(val bulkResponse: BulkResponse) : Exception(
    "Bulk request completed with errors item statuses: [${
        bulkResponse.itemDetails.map { (_, details) -> details.status }.distinct().joinToString(",")
    }]"
)

interface BulkSession {
    suspend fun create(
        source: String,
        index: String? = null,
        id: String? = null,
        requireAlias: Boolean? = null,
        routing: String? = null
    )

    suspend fun index(
        source: String,
        index: String? = null,
        id: String? = null,
        requireAlias: Boolean? = null,
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
        routing: String? = null
    )

    suspend fun delete(id: String, index: String? = null, requireAlias: Boolean? = null, routing: String? = null)

    suspend fun update(
        id: String,
        script: Script,
        index: String? = null,
        requireAlias: Boolean? = null,
        upsert: JsonObject? = null,
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
        routing: String? = null,
    )

    suspend fun update(
        id: String,
        doc: String,
        index: String? = null,
        requireAlias: Boolean? = null,
        docAsUpsert: Boolean? = null,
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
        routing: String? = null,
        )

    suspend fun update(
        id: String,
        doc: JsonObject,
        index: String? = null,
        requireAlias: Boolean? = null,
        docAsUpsert: Boolean? = null,
        ifSeqNo: Int? = null,
        ifPrimaryTerm: Int? = null,
        routing: String? = null,
        )

    suspend fun flush()
}

/**
 * Create using SearchClient.bulk() or SearchClient.bulkSession().
 *
 * Use the [closeOnRequestError] (defaults to true) in combination with a custom [callBack] to
 * handle error situations. The default is to throw an error and close the bulk session.
 *
 * If you set [failOnFirstError] to true (defaults to false), the bulk session will throw a BulkException and the
 * exception will be handled via the [callBack] and close the session if [closeOnRequestError] is true. If you are
 * expecting 100% success for your operations, this allows you to detect when that assumption is broken.
 *
 * @throws BulkException if [failOnFirstError] is true and an item fails to process with an OK status.
 * You may want to configure a [callBack] instead and do something else.
 */
internal class DefaultBulkSession internal constructor(
    val searchClient: SearchClient,
    val failOnFirstError: Boolean = true,
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
    val extraParameters: Map<String, String>? = null,
    val closeOnRequestError: Boolean = true
) : Closeable, BulkSession {
    private val operations: MutableList<Pair<String, String?>> = mutableListOf()
    private var closed: Boolean = false

    override suspend fun create(source: String, index: String?, id: String?, requireAlias: Boolean?, routing: String?) {
        val opDsl = withJsonDsl {
            this["create"] = withJsonDsl {
                index?.let {
                    this["_index"] = index
                }
                id?.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
                routing?.let {
                    this["routing"] = routing
                }
            }
        }
        operation(opDsl.json(), source)
    }

    override suspend fun index(
        source: String,
        index: String?,
        id: String?,
        requireAlias: Boolean?,
        ifSeqNo: Int?,
        ifPrimaryTerm: Int?,
        routing: String?,
    ) {
        val opDsl = withJsonDsl {
            this["index"] = withJsonDsl {
                index?.let {
                    this["_index"] = index
                }
                id?.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
                // used for optimistic locking
                ifSeqNo?.let {
                    this["if_seq_no"] = ifSeqNo
                }
                ifPrimaryTerm?.let {
                    this["if_primary_term"] = ifPrimaryTerm
                }
                routing?.let {
                    this["routing"] = routing
                }
            }
        }
        operation(opDsl.json(), source)
    }

    override suspend fun delete(id: String, index: String?, requireAlias: Boolean?, routing: String?) {
        val opDsl = withJsonDsl {
            this["delete"] = withJsonDsl {
                this["_id"] = id
                index?.let {
                    this["_index"] = index
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
                routing?.let {
                    this["routing"] = routing
                }
            }
        }
        operation(opDsl.json())
    }

    override suspend fun update(
        id: String,
        script: Script,
        index: String?,
        requireAlias: Boolean?,
        upsert: JsonObject?,
        ifSeqNo: Int?,
        ifPrimaryTerm: Int?,
        routing: String?
    ) {
        val opDsl = withJsonDsl {
            this["update"] = withJsonDsl {
                index?.let {
                    this["_index"] = index
                }
                id.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
                // used for optimistic locking
                ifSeqNo?.let {
                    this["if_seq_no"] = ifSeqNo
                }
                ifPrimaryTerm?.let {
                    this["if_primary_term"] = ifPrimaryTerm
                }
                routing?.let {
                    this["routing"] = routing
                }
            }
        }

        // we can't rely on the JsonDsl serializer here because it does not handle kotlinx serialization
        val json =
            """{"script":${script.json()}${upsert?.let { """, "upsert":${DEFAULT_JSON.encodeToString(upsert)}}""" } ?: ""}}""".trimIndent()

        operation(opDsl.json(), json)
    }

    override suspend fun update(
        id: String,
        doc: String,
        index: String?,
        requireAlias: Boolean?,
        docAsUpsert: Boolean?,
        ifSeqNo: Int?,
        ifPrimaryTerm: Int?,
        routing: String?
    ) {
        update(
            id = id,
            doc = DEFAULT_JSON.decodeFromString(JsonObject.serializer(), doc),
            index = index,
            requireAlias = requireAlias,
            docAsUpsert = docAsUpsert,
            ifSeqNo = ifSeqNo,
            ifPrimaryTerm = ifPrimaryTerm,
            routing = routing
        )
    }

    override suspend fun update(
        id: String,
        doc: JsonObject,
        index: String?,
        requireAlias: Boolean?,
        docAsUpsert: Boolean?,
        ifSeqNo: Int?,
        ifPrimaryTerm: Int?,
        routing: String?
    ) {
        val opDsl = withJsonDsl {
            this["update"] = withJsonDsl {
                index?.let {
                    this["_index"] = index
                }
                id.let {
                    this["_id"] = id
                }
                requireAlias?.let {
                    this["require_alias"] = requireAlias
                }
                // used for optimistic locking
                ifSeqNo?.let {
                    this["if_seq_no"] = ifSeqNo
                }
                ifPrimaryTerm?.let {
                    this["if_primary_term"] = ifPrimaryTerm
                }
                routing?.let {
                    this["routing"] = routing
                }
            }
        }

        operation(
            opDsl.json(),
            // ugly but jsondsl doesn't know how to deal with JsonObject
            """{"doc":${DEFAULT_JSON.encodeToString(doc)}${docAsUpsert?.let { ""","doc_as_upsert":$docAsUpsert""" } ?: ""}}"""
        )
    }

    suspend fun operation(operation: String, source: String? = null) {
        verifyOpen()
        operations.add(operation to source)
        if (operations.size > bulkSize) {
            flush()
        }
    }

    override suspend fun flush() {
        verifyOpen()
        if (operations.isNotEmpty()) {
            val ops = mutableListOf<Pair<String, String?>>()
            ops.addAll(operations)
            operations.clear()

            try {
                val response = sendOperations(ops)
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
            } catch (e: Exception) {
                // give user a chance to recover from a failed flush
                // like sleep and resend a few times before permanently failing
                callBack?.bulkRequestFailed(e, ops)
                if (closeOnRequestError) {
                    closed = true
                    throw e
                }
            }
        }
    }

    private fun verifyOpen() {
        if (closed) {
            error("session was closed")
        }
    }

    suspend fun sendOperations(ops: MutableList<Pair<String, String?>>) =
        searchClient.bulk(
            payload = ops.flatMap { listOfNotNull(it.first, it.second) }.joinToString("\n") + "\n",
            target = target,
            pipeline = pipeline,
            refresh = refresh,
            routing = routing,
            timeout = timeout,
            waitForActiveShards = waitForActiveShards,
            requireAlias = requireAlias,
            source = source,
            sourceExcludes = sourceExcludes,
            sourceIncludes = sourceIncludes,
            extraParameters = extraParameters
        )

    /**
     * Closes the bulk session.
     *
     * Attempts to add more operations or flush, will fail after this is called.
     */
    override fun close() {
        closed = true
    }
}

suspend inline fun <reified T> BulkSession.create(
    doc: T,
    index: String? = null,
    id: String? = null,
    requireAlias: Boolean? = null,
    routing: String? = null
) {
    create(DEFAULT_JSON.encodeToString(doc), index, id, requireAlias, routing)
}

suspend inline fun <reified T> BulkSession.index(
    doc: T,
    index: String? = null,
    id: String? = null,
    requireAlias: Boolean? = null,
    routing: String? = null
) {
    index(source = DEFAULT_JSON.encodeToString(doc), index = index, id = id, requireAlias = requireAlias, routing = routing)
}

suspend inline fun <reified T> BulkSession.update(
    script: Script,
    id: String,
    upsert: T,
    index: String? = null,
    requireAlias: Boolean? = null,
) {
    update(
        id,
        script,
        index,
        requireAlias,
        DEFAULT_JSON.encodeToString(upsert).let {
            DEFAULT_JSON.decodeFromString(JsonObject.serializer(), it)
        }
    )
}

suspend inline fun <reified T> BulkSession.update(
    doc: T,
    id: String,
    index: String? = null,
    requireAlias: Boolean? = null,
    docAsUpsert: Boolean? = null,
) {
    val obj = DEFAULT_JSON.encodeToJsonElement(doc).jsonObject
    this.update(
        id = id,
        doc = obj,
        index = index,
        requireAlias = requireAlias,
        docAsUpsert = docAsUpsert
    )
    update(id, obj, index, requireAlias, docAsUpsert)
}

/**
 * Send a single bulk request. Consider using the variant that creates a BulkSession.
 */
suspend fun SearchClient.bulk(
    payload: String,
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
    extraParameters: Map<String, String>? = null,
) =
    restClient.post {
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
        parameter("_source", source)
        parameter("_source_excludes", sourceExcludes)
        parameter("_source_includes", sourceIncludes)
        parameters(extraParameters)

        rawBody(payload)
    }.parse(BulkResponse.serializer(), json)


/**
 * Creates a bulk session that allows you to index, create, or delete items via the convenient kotlin DSL.
 *
 * The BulkSession takes care of firing off bulk requests with the specified bulkSize
 * so you don't have to manually construct bulk requests.
 *
 * @param target if you specify a target, you don't have to specify an index on each operation
 *
 * @param callBack You can use the callback to deal with individual item responses. This is
 * useful for dealing with e.g. version conflicts or other non-fatal item problems.
 * Also, you can get to things like seq_no and primary_term for optimistic locking.
 *
 * @param failOnFirstError If you are expecting all items to succeed, you should set failOnFirstError to true.
 *
 * For the rest of the parameters, see the official bulk REST documentation. All known parameters are supported.
 * Please file a bug if you think something is missing.
 */
suspend fun SearchClient.bulk(
    bulkSize: Int = 100,
    target: String? = null,
    pipeline: String? = null,
    refresh: Refresh? = Refresh.WaitFor, // useful default; change it if you don't like it
    routing: String? = null,
    timeout: Duration? = null,
    waitForActiveShards: String? = null,
    requireAlias: Boolean? = null,
    source: String? = null,
    sourceExcludes: String? = null,
    sourceIncludes: String? = null,
    failOnFirstError: Boolean = false,
    callBack: BulkItemCallBack? = null,
    closeOnRequestError: Boolean = true,
    extraParameters: Map<String, String>? = null,
    block: suspend BulkSession.() -> Unit
) {
    val session = bulkSession(
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
        target = target,
        closeOnRequestError = closeOnRequestError,
        extraParameters = extraParameters
    )

    block.invoke(session)
    // flush remaining items
    session.flush()
}

fun SearchClient.bulkSession(
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
    closeOnRequestError: Boolean = true,
    extraParameters: Map<String, String>? = null,
): BulkSession {
    return DefaultBulkSession(
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
        target = target,
        closeOnRequestError = closeOnRequestError,
        extraParameters = extraParameters
    )
}

