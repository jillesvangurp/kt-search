package com.jillesvangurp.ktsearch

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class CatApiRequestTest {
    @Test
    fun catHealthUsesCatPath() = coRun {
        val restClient = RecordingRestClient()
        val client = SearchClient(restClient)

        client.catHealth()

        restClient.lastPath shouldContain "/_cat/health"
    }

    @Test
    fun catIndicesUsesTargetPath() = coRun {
        val restClient = RecordingRestClient()
        val client = SearchClient(restClient)

        client.catIndices("products-*")

        restClient.lastPath shouldContain "/_cat/indices/products-*"
    }

    @Test
    fun catOptionsAreForwarded() = coRun {
        val restClient = RecordingRestClient()
        val client = SearchClient(restClient)

        client.catNodes(
            nodeId = "node-1",
            options = CatRequestOptions(
                headers = listOf("id", "name"),
                sort = listOf("id"),
                verbose = true,
                help = false,
                bytes = CatBytes.Mb,
                time = CatTime.Ms,
                format = CatFormat.Json,
                local = true,
                extraParameters = mapOf("full_id" to "true"),
            ),
        )

        restClient.lastPath shouldContain "/_cat/nodes/node-1"
        restClient.lastParameters["h"] shouldBe "id,name"
        restClient.lastParameters["s"] shouldBe "id"
        restClient.lastParameters["v"] shouldBe "true"
        restClient.lastParameters["help"] shouldBe "false"
        restClient.lastParameters["bytes"] shouldBe "mb"
        restClient.lastParameters["time"] shouldBe "ms"
        restClient.lastParameters["format"] shouldBe "json"
        restClient.lastParameters["local"] shouldBe "true"
        restClient.lastParameters["full_id"] shouldBe "true"
    }

    @Test
    fun allCoreVariantsProduceExpectedPaths() = coRun {
        val restClient = RecordingRestClient()
        val client = SearchClient(restClient)

        client.catAliases("alias")
        restClient.lastPath shouldContain "/_cat/aliases/alias"

        client.catAllocation("node")
        restClient.lastPath shouldContain "/_cat/allocation/node"

        client.catCount("idx")
        restClient.lastPath shouldContain "/_cat/count/idx"

        client.catMaster()
        restClient.lastPath shouldContain "/_cat/master"

        client.catPendingTasks()
        restClient.lastPath shouldContain "/_cat/pending_tasks"

        client.catRecovery("idx")
        restClient.lastPath shouldContain "/_cat/recovery/idx"

        client.catRepositories()
        restClient.lastPath shouldContain "/_cat/repositories"

        client.catShards("idx")
        restClient.lastPath shouldContain "/_cat/shards/idx"

        client.catSnapshots("repo")
        restClient.lastPath shouldContain "/_cat/snapshots/repo"

        client.catTasks()
        restClient.lastPath shouldContain "/_cat/tasks"

        client.catTemplates("tpl")
        restClient.lastPath shouldContain "/_cat/templates/tpl"

        client.catThreadPool("search")
        restClient.lastPath shouldContain "/_cat/thread_pool/search"
    }
}

private class RecordingRestClient : RestClient {
    var lastPath: List<String> = emptyList()
    var lastParameters: Map<String, Any> = emptyMap()

    override suspend fun nextNode(): Node {
        return Node("localhost", 9200)
    }

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
        return RestResponse.Status2XX.OK("[]".encodeToByteArray())
    }
}
