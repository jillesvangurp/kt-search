package com.jillesvangurp.ktsearch

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal data class VerifiedNode(val lastChecked: Instant, val node: Node)

/**
 * If you are running against a cluster without a node balancer
 * you can use this node selector to make the client sniff nodes from the cluster.
 *
 * It will periodically sniff the cluster using the [maxNodeAge] parameter.
 * You bootstrap it with at least 1 [initialNodes].
 *
 * The nextNode() function tries to return the same node for the same thread (jvm only)
 * or AffinityId scope (you must set this up in your co routine scope). It falls back
 * to randomly returning a node if no affinity or thread id is available.
 */
class SniffingNodeSelector(
    private vararg val initialNodes: Node,
    private val maxNodeAge: Duration = 1.minutes,
    private val https: Boolean = false,
    private val user: String? = null,
    private val password: String? = null,
) : NodeSelector {

    // used as a primitive lock; not fool proof but worst case it just calls the nodes API an extra time.
    private var sniffing: Boolean = false

    companion object {
        val sniffingScope by lazy { CoroutineScope(CoroutineName("node-sniffer")) }
    }

    private var knownNodes: List<VerifiedNode> = emptyList()
    private val affinity: MutableMap<String, Node> = mutableMapOf()

    private fun createClient(node: Node) = SearchClient(
        restClient = KtorRestClient(
            host = node.host,
            port = node.port ?: 9200,
            https = https,
            user = user,
            password = password
        )
    )

    private fun JsonObject.extractNodes(): List<Node> {
        val nodes = this["nodes"]?.jsonObject
        return nodes?.mapNotNull { (_, node) ->
            node.jsonObject.let { value ->
                value["http"]?.jsonObject?.let { http ->
                    http["publish_address"]?.jsonPrimitive?.content?.split(":")?.let { splitted ->
                        Node(splitted[0], splitted[1].toInt())
                    }
                }
            }
        } ?: listOf()
    }

    private suspend fun sniffNode(node: Node): List<Node>? {
        return try {
            createClient(node).restClient.get {
                path("_nodes", "http")
            }.parseJsonObject().extractNodes()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun sniffAll() {
        // slight risk of double sniffing here from different threads,
        // won't hurt anyone if that happens
        // not worth introducing some locking mechanism here
        if (sniffing) {
            return
        }
        try {
            sniffing = true
            val nodes = knownNodes.firstNotNullOfOrNull {
                sniffNode(it.node)
            } ?: initialNodes.firstNotNullOfOrNull {
                sniffNode(it)
            }
            if (nodes?.isNotEmpty() == true) {
                val lastChecked = Clock.System.now()
                knownNodes = nodes.map {
                    VerifiedNode(lastChecked, it)
                }
                // create a copy to avoid ConcurrentModificationException
                affinity.entries.toList().forEach { (aid, an) ->
                    if (knownNodes.firstOrNull { it.node == an } == null) {
                        affinity.remove(aid)
                    }
                }
            } else {
            }
        } finally {
            sniffing = false
        }
    }

    override suspend fun selectNode(): Node {
        val now = Clock.System.now()
        if(knownNodes.isEmpty() && !sniffing) {
            knownNodes = initialNodes.map { VerifiedNode(now,it) }
        }
        return coroutineScope {
            val affinityId = coroutineContext[AffinityId.Key]?.affinityId ?: threadId()
            if (affinityId != null) {
                val usableNode = knownNodes.firstOrNull { it.node == affinity[affinityId] }
                if (usableNode == null) {
                        val aNode = knownNodes.random()
                        affinity[affinityId] = aNode.node
                        aNode
                } else {
                    usableNode
                }
            } else {
                knownNodes.random()
            }.also {
                if (it.lastChecked < (now - maxNodeAge)) {
                    sniffingScope.launch {
                        sniffAll()
                    }
                }
            }
        }.node
    }
}

/**
 * On JVM this will return the current thread name,  on kotlin-js this will return null
 */
expect fun threadId(): String?

/**
 * Use this co routine scope to control affinity to a particular host.
 * Ktor client may attempt to pipeline/reuse connections depending on how you set that up.
 */
class AffinityId(val affinityId: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<AffinityId>

    override fun toString(): String = """AffinityId($affinityId)"""
}