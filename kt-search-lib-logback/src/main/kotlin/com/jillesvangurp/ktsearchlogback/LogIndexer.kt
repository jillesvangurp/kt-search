package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Buffers log events for bulk creation in [index] using [client]. */
class LogIndexer(
    private val client: SearchClient,
    private val index: String,
    private val bulkMaxPageSize: Int,
    private val flushSeconds: Int,
) {
    private var session: BulkSession
    internal val eventChannel = Channel<LogMessage>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val flushJob: Job
    private val indexJob: Job

    private var lastIndexed: Instant? = null
    private var running = true

    private val loggingScope = CoroutineScope(Dispatchers.Default + CoroutineName("kt-search"))

    private var successCount=0
    private var failCount=0
    private var errorCount=0
    private var receiveCount=0

    init {
        try {
            session = runBlocking {
                 client.bulkSession(
                    bulkSize = bulkMaxPageSize,
                    closeOnRequestError = false,
                    failOnFirstError = false,
                    // do some bookkeeping so you can know when you are losing messages
                    callBack = object : BulkItemCallBack {
                        override fun bulkRequestFailed(e: Exception, ops: List<Pair<String, String?>>) {
                            println("bulkRequest failed ${e.message}")
                            e.printStackTrace()
                            errorCount++
                        }

                        override fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails) {
                            println("bulkItem failed ${operationType.name}")
                            failCount++
                        }

                        override fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails) {
                            successCount++
                        }

                    }
                )
            }
            flushJob = loggingScope.launch {
                try {
                    while (running) {
                        val now = Clock.System.now()
                        val check = lastIndexed
                        if (check != null) {
                            if (now.minus(check).inWholeSeconds > flushSeconds) {
                                try {
                                    session.flush()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    println("Error flushing: ${e.message}")
                                }
                                lastIndexed = now
                            }
                        } else {
                            lastIndexed = now
                        }
                        delay(1.seconds)
                    }
                } catch (e: CancellationException) {
                    if(running) {
                        warn(e,"Logback appender flush loop cancelled before running set to false")
                    }
                } catch (e: Exception) {
                    warn(e, "Logback appender flush loop exiting abnormally")
                }
            }
            indexJob = loggingScope.launch {
                try {
                    while (running) {
                        try {
                            try {
                                withTimeout(50.milliseconds) {
                                    val logMessage = eventChannel.receive()
                                    receiveCount++
                                    session.create(index = index, doc = logMessage)
                                }
                            } catch(_: CancellationException) {
                                // we want to re-evaluate regularly whether we can exit the loop
                            }
                        } catch (e: Exception) {

                            warn(e,"error processing logMessage")
                        }
                    }
                } catch (e: Exception) {
                    warn(e, "Logback appender session create loop exiting abnormally")
                }
            }
            log("Started successfully")
        } catch (e: Exception) {
            warn(e,"Error initializing kt-search log appender")
            throw e
        }
    }

    /** Flushes the current bulk session and stops the indexing jobs. */
    fun stop() {
        println("stopping")
        runBlocking {
            println("stop accepting new messages")
            running = false
            session.flush()
            println("flushed remaining messages")
            runCatching {
                if(flushJob.isActive) {
                    // should have died by now; but just in case
                    println("flush job was still active, cancelling")
                    flushJob.cancel()
                }
            }
        }
        indexJob.cancel()
        println("closed es logback appender: received: $receiveCount, indexed:$successCount, failed: $failCount, errors: $errorCount")
    }
}
