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
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.xcontent.XContentType
import org.elasticsearch.rest.RestStatus
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val logger = KotlinLogging.logger {}

data class BulkOperation<T : Any>(
        val operation: DocWriteRequest<*>,
        val id: String?,
        val updateFunction: ((T) -> T)? = null,
        val itemCallback: (BulkOperation<T>, BulkItemResponse) -> Unit = { _, _ -> }
)

/**
 * Makes using bulk request easier. You can use this directly but you probably want to use it via [IndexRepository]. Implements `AutoCloseable` to ensure all operations are processed.
 *
 * ```
 * repository.bulk() {
 *   index("xxx",myObject)
 *   index("yyy",anotherObject)
 *   delete("zzz")*
 * }
 * ```
 *
 * @param client
 * @param repository
 * @param modelReaderAndWriter Defaults to the one configured on the repository.
 * @param bulkSize override this to change the bulk page size (the number of items sent to ES with one request).
 * @param retryConflictingUpdates the default `itemCallback` is capable of retrying updates. When retrying it will get the document and try again. The default for this is 0.
 * @param refreshPolicy The bulk API returns a response that contains a per item response. This callback facilitates dealing with e.g. failures. The default implementation does logging and update retries.
 * @param itemCallback Override request options if you need to. Defaults to those configured on the repository.
 * @param defaultRequestOptions Defaults to what you configured on the repository
 *
 */
class BulkIndexingSession<T : Any>(
        private val client: RestHighLevelClient,
        private val repository: IndexRepository<T>,
        private val modelReaderAndWriter: ModelReaderAndWriter<T> = repository.modelReaderAndWriter,
        private val bulkSize: Int = 100,
        private val retryConflictingUpdates: Int = 0,
        private val refreshPolicy: WriteRequest.RefreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL,
        private val itemCallback: ((BulkOperation<T>, BulkItemResponse) -> Unit)? = null,
        private val defaultRequestOptions: RequestOptions = repository.defaultRequestOptions

) : AutoCloseable {
    // adding things to a request should be thread safe
    private val page = ConcurrentLinkedDeque<BulkOperation<T>>()
    private val closed = AtomicBoolean(false)

    // we use rw lock to protect the current page. read here means using the list (to add stuff), write means  building the bulk request and clearing the list.
    private val rwLock = ReentrantReadWriteLock()

    private fun defaultItemResponseCallback(operation: BulkOperation<T>, itemResponse: BulkItemResponse) {
        if (itemCallback == null) {
            if (itemResponse.isFailed) {
                if (retryConflictingUpdates > 0 && DocWriteRequest.OpType.UPDATE === itemResponse.opType && itemResponse.failure.status === RestStatus.CONFLICT) {
                    repository.update(
                        id = operation.id?:error("id is required for updates"),
                        maxUpdateTries = retryConflictingUpdates,
                        requestOptions = defaultRequestOptions,
                        refreshPolicy = WriteRequest.RefreshPolicy.NONE, // don't block on retries
                        transformFunction = operation.updateFunction!!
                    )
                    logger.debug { "retried updating ${operation.id} after version conflict" }
                } else {
                    logger.warn { "failed item ${itemResponse.itemId} ${itemResponse.opType} on ${itemResponse.id} because ${itemResponse.failure.status} ${itemResponse.failureMessage}" }
                }
            }
        } else {
            itemCallback.invoke(operation, itemResponse)
        }
    }

    /**
     * Index an object.
     *
     * @param create set to true for upsert
     */
    @Suppress("DEPRECATION") // we allow using types for now
    fun index(id: String?, obj: T, create: Boolean = true, itemCallback: (BulkOperation<T>, BulkItemResponse) -> Unit = this::defaultItemResponseCallback) {
        check(!closed.get()) { "cannot add bulk operations after the BulkIndexingSession is closed" }
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
        rwLock.read {
            page.add(
                    BulkOperation(
                            indexRequest,
                            id,
                            itemCallback = itemCallback
                    )
            )
        }
        flushIfNeeded()
    }

    /**
     * Safe way to bulk update objects. Gets the object from the index first before applying the lambda to it to modify the existing object. If you set `retryConflictingUpdates` > 0, it will attempt to retry to get the latest document and apply the `updateFunction` if there is a version conflict.
     */
    fun getAndUpdate(id: String, itemCallback: (BulkOperation<T>, BulkItemResponse) -> Unit = this::defaultItemResponseCallback, updateFunction: (T) -> T) {
        val pair = repository.getWithGetResponse(id)
        if (pair != null) {
            val (retrieved, resp) = pair
            update(id, resp.seqNo, resp.primaryTerm, retrieved, itemCallback, updateFunction)
        }
    }

    /**
     * Bulk update objects. If you have the object (e.g. because you are processing the sequence of a scrolling search), you can update what you have in a safe way.  If you set `retryConflictingUpdates` > 0, it will retry by getting the latest version and re-applying the `updateFunction` in case of a version conflict.
     */
    @Suppress("DEPRECATION")
    fun update(id: String, seqNo: Long, primaryTerms: Long, original: T, itemCallback: (BulkOperation<T>, BulkItemResponse) -> Unit = this::defaultItemResponseCallback, updateFunction: (T) -> T) {
        check(!closed.get()) { "cannot add bulk operations after the BulkIndexingSession is closed" }
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
        rwLock.read {
            page.add(
                    BulkOperation(
                            updateRequest,
                            id,
                            updateFunction = updateFunction,
                            itemCallback = itemCallback
                    )
            )
        }
        flushIfNeeded()
    }

    /**
     * Delete an object from the index.
     */
    @Suppress("DEPRECATION")
    fun delete(id: String, seqNo: Long? = null, term: Long? = null, itemCallback: (BulkOperation<T>, BulkItemResponse) -> Unit = this::defaultItemResponseCallback) {
        check(!closed.get()) { "cannot add bulk operations after the BulkIndexingSession is closed" }
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
        rwLock.read {
            page.add(
                    BulkOperation(
                            deleteRequest,
                            id,
                            itemCallback = itemCallback
                    )
            )
        }
        flushIfNeeded()
    }

    private fun flushIfNeeded() {
        if (page.size >= bulkSize) {
            flush()
        }
    }

    private fun flush() {
        if (page.size > 0) {
            // make sure nobody writes to the list
            val pageClone = mutableListOf<BulkOperation<T>>()
            rwLock.write {
                // check if some other thread did not beat us
                if (page.size > 0) {
                    page.forEach { pageClone.add(it) }
                    page.clear()
                }
            }
            val bulkRequest = BulkRequest()
            bulkRequest.refreshPolicy = refreshPolicy
            pageClone.forEach { bulkRequest.add(it.operation) }
            logger.debug { "flushing ${page.size} items" }
            val bulkResponse = client.bulk(bulkRequest, defaultRequestOptions)
            bulkResponse?.items?.forEach {
                val bulkOperation = pageClone[it.itemId]
                bulkOperation.itemCallback.invoke(bulkOperation, it)
            }
        }
    }

    /**
     * Prevents further operations from being accepted and calls flush one more time to ensure already accepted items get processed.
     */
    override fun close() {
        closed.set(true) // stop accepting operations
        flush()
    }
}
