package com.jillesvangurp.eskotlinwrapper

import mu.KotlinLogging
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BulkItemResponse
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.delete.DeleteRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.bulkAsync
import org.elasticsearch.rest.RestStatus
import org.elasticsearch.xcontent.XContentType
import java.time.Duration

private val logger = KotlinLogging.logger {}

data class AsyncBulkOperation<T : Any>(
    val operation: DocWriteRequest<*>,
    val id: String?,
    val updateFunction: suspend ((T) -> T) = { it },
    val itemCallback: suspend (AsyncBulkOperation<T>, BulkItemResponse) -> Unit
)

/**
 * Asynchronous bulk indexing. Works similar to the synchronous version except
 * it fires bulk requests asynchronously.
 */
class AsyncBulkIndexingSession<T : Any> private constructor(
    // private constructor because you should use the companion object which has a function that flushes after use
    private val repository: AsyncIndexRepository<T>,
    private val modelReaderAndWriter: ModelReaderAndWriter<T> = repository.modelReaderAndWriter,
    private val itemCallback: suspend ((AsyncBulkOperation<T>, BulkItemResponse) -> Unit),
    private val defaultRequestOptions: RequestOptions = repository.defaultRequestOptions,
    private val bulkSize: Int = 100,
    private val refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL,
) {
    // expose some statistics
    var opsCount = 0
        private set
    var flushCount = 0
        private set
    var successCount = 0
        private set
    var failCount = 0
        private set
    val startTime = System.currentTimeMillis()

    private val buffer = mutableListOf<AsyncBulkOperation<T>>()

    companion object {
        suspend fun <T : Any> asyncBulk(
            repository: AsyncIndexRepository<T>,
            modelReaderAndWriter: ModelReaderAndWriter<T> = repository.modelReaderAndWriter,
            bulkSize: Int = 100,
            retryConflictingUpdates: Int = 0,
            refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL,
            itemCallback: suspend ((AsyncBulkOperation<T>, BulkItemResponse) -> Unit) = { operation, itemResponse ->
                if (itemResponse.isFailed) {
                    if (retryConflictingUpdates > 0 && DocWriteRequest.OpType.UPDATE === itemResponse.opType && itemResponse.failure.status === RestStatus.CONFLICT) {
                        repository.update(
                            id = operation.id ?: error("id is required for update"),
                            maxUpdateTries = retryConflictingUpdates,
                            requestOptions = defaultRequestOptions,
                            refreshPolicy = WriteRequest.RefreshPolicy.NONE, // don't block on retries
                            transformFunction = operation.updateFunction
                        )
                        logger.debug { "retried updating ${operation.id} after version conflict" }
                    } else {
                        logger.warn { "failed item ${itemResponse.itemId} ${itemResponse.opType} on ${itemResponse.id} because ${itemResponse.failure.status} ${itemResponse.failureMessage}" }
                    }
                }
            },
            defaultRequestOptions: RequestOptions = repository.defaultRequestOptions,
            block: suspend AsyncBulkIndexingSession<T>.() -> Unit,
        ) {
            val session = AsyncBulkIndexingSession(
                repository,
                modelReaderAndWriter,
                itemCallback,
                defaultRequestOptions,
                bulkSize,
                refreshPolicy
            )
            block.invoke(session)
            // flush remaining items
            session.flush()
            if (session.flushCount != session.opsCount) {
                logger.warn { "Bulk session finished with mismatched ops count of ${session.opsCount} and flushed items count of ${session.flushCount}." }
            }
            if (session.successCount + session.failCount < session.opsCount) {
                logger.warn { "Not all bulk operations are accounted for. Success: ${session.successCount}. Failed: ${session.failCount} out of ${session.opsCount} bulk operations." }
            }
            val duration = Duration.ofMillis(System.currentTimeMillis() - session.startTime)
            logger.debug { "Finished in ${duration.seconds} seconds. Processed ${session.opsCount} ${if (session.failCount == 0) "All items succeeded" else "${session.failCount} failed items, ${session.successCount}"}" }
        }
    }

    @Suppress("DEPRECATION")
    suspend fun index(
        id: String?,
        obj: T,
        create: Boolean = true
    ) {

        val indexRequest = IndexRequest()
            .index(repository.indexWriteAlias)
            .source(modelReaderAndWriter.serialize(obj), XContentType.JSON)
            .let {
                if (id == null) {
                    it
                } else {
                    it.id(id).create(create)
                }
            }
        if (!repository.type.isNullOrBlank()) {
            indexRequest.type(repository.type)
        }
        addOp(AsyncBulkOperation(operation = indexRequest, id = id, itemCallback = itemCallback))
    }

    /**
     * Safe way to bulk update objects. Gets the object from the index first before applying the lambda to it to modify the existing object. If you set `retryConflictingUpdates` > 0, it will attempt to retry to get the latest document and apply the `updateFunction` if there is a version conflict.
     */
    suspend fun getAndUpdate(
        id: String,
        updateFunction: suspend (T) -> T
    ) {
        val pair = repository.getWithGetResponse(id)
        if (pair != null) {
            val (retrieved, resp) = pair
            update(id, resp.seqNo, resp.primaryTerm, retrieved, updateFunction)
        }
    }

    /**
     * Bulk update objects. If you have the object (e.g. because you are processing the sequence of a scrolling search), you can update what you have in a safe way.  If you set `retryConflictingUpdates` > 0, it will retry by getting the latest version and re-applying the `updateFunction` in case of a version conflict.
     */
    @Suppress("DEPRECATION") // we allow using types for now
    suspend fun update(
        id: String,
        seqNo: Long,
        primaryTerms: Long,
        original: T,
        updateFunction: suspend (T) -> T
    ) {
        val updateRequest = UpdateRequest()
            .index(repository.indexWriteAlias)
            .id(id)
            .detectNoop(true)
            .setIfSeqNo(seqNo)
            .setIfPrimaryTerm(primaryTerms)
            .doc(modelReaderAndWriter.serialize(updateFunction.invoke(original)), XContentType.JSON)
        if (!repository.type.isNullOrBlank()) {
            updateRequest.type(repository.type)
        }
        addOp(
            AsyncBulkOperation(
                updateRequest,
                id,
                updateFunction = updateFunction,
                itemCallback = itemCallback
            )
        )
    }

    /**
     * Delete an object from the index.
     */
    @Suppress("DEPRECATION") // we allow using types for now
    suspend fun delete(
        id: String,
        seqNo: Long? = null,
        term: Long? = null
    ) {
        val deleteRequest = DeleteRequest()
            .index(repository.indexWriteAlias)
            .id(id)

        if (!repository.type.isNullOrBlank()) {
            deleteRequest.type(repository.type)
        }

        if (seqNo != null) {
            deleteRequest.setIfSeqNo(seqNo)
            if (term != null) {
                deleteRequest.setIfPrimaryTerm(term)
            }
        }
        addOp(
            AsyncBulkOperation(
                deleteRequest,
                id,
                itemCallback = itemCallback
            )
        )
    }

    private suspend fun addOp(op: AsyncBulkOperation<T>) {
        opsCount++
        synchronized(buffer) {
            // just in case somebody decides to use threads, lets guard list modifications
            buffer.add(op)
        }
        if (buffer.size > bulkSize) {
            flush()
        }
    }

    private suspend fun flush() {
        if (buffer.isNotEmpty()) {
            val ops = mutableListOf<AsyncBulkOperation<T>>()
            synchronized(buffer) {
                // just in case somebody decides to use threads, lets guard list modifications
                ops.addAll(buffer)
                buffer.clear()
            }
            val bulkRequest = BulkRequest()
            bulkRequest.refreshPolicy = refreshPolicy
            ops.forEach { bulkRequest.add(it.operation) }
            repository.client.bulkAsync(bulkRequest, defaultRequestOptions).items?.forEach {
                val bulkOperation = ops[it.itemId]
                if (it.isFailed) {
                    failCount++
                } else {
                    successCount++
                }
                bulkOperation.itemCallback.invoke(bulkOperation, it)
            }
            flushCount += ops.size
        }
    }
}
