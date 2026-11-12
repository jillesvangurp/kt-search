package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


/** Bulk indexes Logback events using the configured KT Search connection. */
@Suppress("MemberVisibilityCanBePrivate", "unused") // we need public properties
class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {
    // you can override all the public properties via the logback xml config

    /** Will log a lot of detail. Useful for debugging when the appender isn't working as expected. */
    var verbose = false

    /** Leave this off unless you have an issue with elasticsearch that you need to diagnose */
    var logElasticSearchCalls = false
    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

    /** maximum time to wait until flushing messages to Elasticsearch */
    var flushSeconds: Int = 5

    /** maximum bulk request page size before flushing. */
    var bulkMaxPageSize: Int = 200

    var dataStreamName = "applogs"
    var contextVariableFilterRe = ""

    /** comma separated list of mdc fields (without mdc prefix), will be coerced to Long in the json */
    var coerceMdcFieldsToLong = ""
    var coerceMdcFieldsToDouble = ""

    private val contextVariableFilter: Regex? by lazy {
        contextVariableFilterRe.takeIf { it.isNotBlank() }?.let {
            try {
                it.toRegex()
            } catch (e: Exception) {
                log("Error parsing $it: ${e.message}")
                null
            }
        }
    }
    private lateinit var logIndexer: LogIndexer
    private lateinit var client: SearchClient

    private val coerceLongFields by lazy { coerceMdcFieldsToLong.split(',').map { it.trim() }.toSet() }
    private val coerceDoubleFields by lazy { coerceMdcFieldsToDouble.split(',').map { it.trim() }.toSet() }

    /** Starts the asynchronous client and bulk indexer. */
    override fun start() {
        super.start()
        // initialize client from a co-routine after logging has had a chance to init
        // because ktor-client calls logging related APIs during it's initialization
        // this way the appender start exits before any ktor-client stuff is called
        CoroutineScope(CoroutineName("startup-init")).launch {
            try {
                println("starting logging-es-calls: $logElasticSearchCalls")

                client = SearchClient(
                    KtorRestClient(
                        host = host,
                        port = port,
                        user = userName,
                        password = password,
                        https = ssl,
                        logging = logElasticSearchCalls,
                    )
                )
                log("connecting to $host:$port using ssl $ssl with user: $userName and password: ${password?.map { 'x' }}")
                log("""
                    coerceLongFields: $coerceLongFields
                    coerceDoubleFields: $coerceDoubleFields
                """.trimIndent())
                logIndexer = LogIndexer(client, dataStreamName, bulkMaxPageSize, flushSeconds)
                log("started log indexer")
                // so you can detect application restarts
                Runtime.getRuntime().addShutdownHook(Thread {
                    // does not seem to ever get called otherwise
                    logIndexer.stop()
                })
            } catch (e: Exception) {
                println("ERROR starting log appender: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /** Queues [eventObject] for bulk indexing. */
    override fun append(eventObject: ILoggingEvent?) {
        if (eventObject != null) {
            logIndexer.eventChannel.trySend(
                eventObject.toLogMessage(
                    contextVariableFilter,
                    coerceLongFields,
                    coerceDoubleFields
                )
            )
        }
    }

    /** Flushes pending bulk operations and stops indexing. */
    override fun stop() {
        log("appender stopping")
        logIndexer.stop()
    }
}
