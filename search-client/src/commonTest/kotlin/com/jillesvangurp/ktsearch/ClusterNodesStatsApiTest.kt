package com.jillesvangurp.ktsearch

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class ClusterNodesStatsApiTest {
    @Test
    fun clusterStatsUsesPathAndFilterPath() = coRun {
        val restClient = RecordingStatsRestClient("""{"cluster_name":"demo"}""")
        val client = SearchClient(restClient)

        client.clusterStats(
            filterPath = listOf("cluster_name", "status"),
        )

        restClient.lastPath shouldContain "/_cluster/stats"
        restClient.lastParameters["filter_path"] shouldBe
            "cluster_name,status"
    }

    @Test
    fun nodesStatsUsesMetricPathAndFilterPath() = coRun {
        val restClient = RecordingStatsRestClient("""{"cluster_name":"demo"}""")
        val client = SearchClient(restClient)

        client.nodesStats(
            metrics = listOf("os", "jvm"),
            filterPath = listOf("cluster_name", "nodes.*.name"),
        )

        restClient.lastPath shouldContain "/_nodes/stats/os,jvm"
        restClient.lastParameters["filter_path"] shouldBe
            "cluster_name,nodes.*.name"
    }

    @Test
    fun decodesClusterAndNodeStatsSubset() = coRun {
        val clusterPayload = """
            {
              "cluster_name": "demo",
              "status": "green",
              "indices": {
                "docs": { "count": 1000 },
                "shards": { "total": 5 },
                "store": { "size_in_bytes": 2048 },
                "segments": {
                  "count": 22,
                  "memory_in_bytes": 4096
                }
              },
              "nodes": {
                "count": { "total": 1 }
              }
            }
        """.trimIndent()
        val nodesPayload = """
            {
              "cluster_name": "demo",
              "nodes": {
                "node-id-1": {
                  "name": "n-1",
                  "roles": ["master", "data"],
                  "os": { "cpu": { "percent": 34 } },
                  "jvm": {
                    "mem": {
                      "heap_used_in_bytes": 1024,
                      "heap_max_in_bytes": 4096,
                      "heap_used_percent": 25
                    }
                  },
                  "fs": {
                    "total": {
                      "total_in_bytes": 10000,
                      "available_in_bytes": 6000
                    }
                  },
                  "indices": {
                    "docs": { "count": 1000 },
                    "store": { "size_in_bytes": 2048 },
                    "segments": {
                      "count": 22,
                      "memory_in_bytes": 4096
                    },
                    "indexing": { "index_total": 100 },
                    "search": { "query_total": 200 }
                  },
                  "thread_pool": {
                    "search": {
                      "active": 1,
                      "queue": 2,
                      "rejected": 3
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val clusterClient = SearchClient(RecordingStatsRestClient(clusterPayload))
        val nodesClient = SearchClient(RecordingStatsRestClient(nodesPayload))

        val cluster = clusterClient.clusterStats()
        val nodes = nodesClient.nodesStats()

        cluster.clusterName shouldBe "demo"
        cluster.indices?.docs?.count shouldBe 1000
        cluster.indices?.segments?.memoryInBytes shouldBe 4096
        nodes.clusterName shouldBe "demo"
        nodes.nodes["node-id-1"]?.jvm?.mem?.heapUsedPercent shouldBe 25
        nodes.nodes["node-id-1"]?.threadPool?.get("search")?.queue shouldBe 2
    }
}

private class RecordingStatsRestClient(
    private val payload: String,
) : RestClient {
    var lastPath: List<String> = emptyList()
    var lastParameters: Map<String, Any> = emptyMap()

    override suspend fun nextNode(): Node = Node("localhost", 9200)

    override fun close() {
    }

    override suspend fun doRequest(
        pathComponents: List<String>,
        httpMethod: HttpMethod,
        parameters: Map<String, Any>?,
        payload: String?,
        contentType: String,
        headers: Map<String, Any>?,
    ): RestResponse {
        lastPath = pathComponents
        lastParameters = parameters ?: emptyMap()
        return RestResponse.Status2XX.OK(this.payload.encodeToByteArray())
    }
}
