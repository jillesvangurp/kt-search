package com.jillesvangurp.ktsearch

import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class PitPagingApiTest {
    @Test
    fun strictCreationMapsTheEngineParameterAndClosesPartialSnapshots() = coRun {
        for (openSearch in listOf(false, true)) {
            val rest = PitRecordingClient(openSearch)
            val client = SearchClient(rest)
            client.createPointInTime("test", 60.seconds, false) shouldBe "snapshot"
            val create = rest.calls.last()
            create.path.last() shouldBe if (openSearch) "point_in_time" else "_pit"
            create.parameters[if (openSearch) "allow_partial_pit_creation" else "allow_partial_search_results"] shouldBe "false"
            rest.creationShards = """{"total":2,"successful":1,"failed":1}"""
            shouldThrow<IllegalStateException> { client.createPointInTime("test", 60.seconds, false) }
            rest.calls.last().method shouldBe HttpMethod.Delete
        }
    }

    @Test
    fun oldOverloadPreservesDefaultAndSupportsIdOnlyCreation() = coRun {
        val rest = PitRecordingClient(false)
        rest.creationShards = null
        val client = SearchClient(rest)
        client.createPointInTime("test", 60.seconds) shouldBe "snapshot"
        rest.calls.last().parameters.keys.any { it.startsWith("allow_partial") } shouldBe false
        client.createPointInTime("test", 60.seconds, false) shouldBe "snapshot"
    }

    @Test
    fun olderElasticsearchUsesItsStrictDefaultWithoutUnsupportedParameters() = coRun {
        for (version in listOf("7.17.0", "8.0.0", "8.15.0", "8.16.0")) {
            val rest = PitRecordingClient(false, version)
            val client = SearchClient(rest)
            client.createPointInTime("test", 60.seconds, false) shouldBe "snapshot"
            rest.calls.last().parameters["allow_partial_search_results"] shouldBe
                if (version == "8.16.0") "false" else null
            if (version == "8.16.0") {
                client.createPointInTime("test", 60.seconds, true) shouldBe "snapshot"
                rest.calls.last().parameters["allow_partial_search_results"] shouldBe "true"
            } else {
                val count = rest.calls.size
                shouldThrow<IllegalArgumentException> { client.createPointInTime("test", 60.seconds, true) }
                rest.calls.size shouldBe count
            }
        }
    }

    @Test
    fun independentPagesPreserveTypedSortValuesAndStorageIds() = coRun {
        for (openSearch in listOf(false, true)) {
            val rest = PitRecordingClient(openSearch)
            val client = SearchClient(rest)
            val after = if (openSearch) JsonArray(listOf(JsonPrimitive("index-a"), JsonPrimitive("storage-a")))
                else JsonArray(listOf(JsonPrimitive(922337203685477580L)))
            val response = client.searchPointInTimePage("snapshot", 60.seconds, "storageKey", after) {
                resultSize = 2
            }
            val call = rest.calls.last()
            call.parameters["allow_partial_search_results"] shouldBe "false"
            val body = DEFAULT_JSON.parseToJsonElement(requireNotNull(call.payload)).jsonObject
            body["search_after"] shouldBe after
            body["track_total_hits"] shouldBe JsonPrimitive("true")
            val sort = body.getValue("sort").jsonArray
            sort.size shouldBe if (openSearch) 2 else 1
            response.searchHits.single().id shouldBe "storage-id"
            response.searchHits.single().source?.get("id") shouldBe JsonPrimitive("payload-id")
        }
    }

    @Test
    fun independentPagesRejectIncompleteResponsesAndConflictingPaging() = coRun {
        val rest = PitRecordingClient(true)
        val client = SearchClient(rest)
        for (failure in listOf("timeout", "shards", "total", "sort")) {
            rest.failure = failure
            val error = shouldThrow<IncompletePitPageException> {
                client.searchPointInTimePage("snapshot", 60.seconds, "storageKey") {}
            }
            error.pitId shouldBe "snapshot"
        }
        shouldThrow<IllegalArgumentException> {
            client.searchPointInTimePage("snapshot", 60.seconds, "storageKey") { from = 1 }
        }
        shouldThrow<IllegalArgumentException> {
            client.searchPointInTimePage("snapshot", 60.seconds, "_id") {}
        }
        shouldThrow<IllegalArgumentException> {
            client.searchPointInTimePage("snapshot", 60.seconds, "storageKey") { this["sort"] = listOf("id") }
        }
    }

    @Test
    fun shardStatsKeepMissingValuesDistinctFromZero() = coRun {
        val rest = PitRecordingClient(false)
        val result = SearchClient(rest).indexStats("source", IndexStatsLevel.Shards)
        rest.calls.last().parameters["level"] shouldBe "shards"
        val index = result.indices.getValue("source")
        index.uuid shouldBe "uuid-1"
        val copies = index.shards.getValue("0")
        copies[0].routing?.primary shouldBe true
        copies[0].seqNo?.maxSeqNo shouldBe 922337203685477580L
        copies[1].seqNo?.maxSeqNo shouldBe null
    }
}

private data class PitRecordedCall(
    val path: List<String>, val method: HttpMethod,
    val parameters: Map<String, Any>, val payload: String?,
)

private class PitRecordingClient(private val openSearch: Boolean, private val version: String = if(openSearch) "3.10.1" else "9.1.3") : RestClient {
    val calls = mutableListOf<PitRecordedCall>()
    var creationShards: String? = """{"total":2,"successful":2,"failed":0}"""
    var failure: String? = null
    override suspend fun nextNode() = Node("localhost", 9200)
    override fun close() = Unit
    override suspend fun doRequest(
        pathComponents: List<String>, httpMethod: HttpMethod, parameters: Map<String, Any>?,
        payload: String?, contentType: String, headers: Map<String, Any>?,
    ): RestResponse {
        val path = pathComponents.flatMap { it.split('/') }.filter { it.isNotEmpty() }
        calls.add(PitRecordedCall(path, httpMethod, parameters.orEmpty(), payload))
        val text = when {
            path.isEmpty() -> """{"name":"test","cluster_name":"test","cluster_uuid":"uuid","version":{"distribution":"${if(openSearch) "opensearch" else "elasticsearch"}","number":"${version}","build_hash":"hash","build_date":"date","build_snapshot":false,"lucene_version":"10","minimum_wire_compatibility_version":"7","minimum_index_compatibility_version":"7"}}"""
            httpMethod == HttpMethod.Delete -> "{}"
            path.last() == "_pit" || path.last() == "point_in_time" -> buildJsonObject {
                put(if (openSearch) "pit_id" else "id", "snapshot")
                creationShards?.let { put("_shards", DEFAULT_JSON.parseToJsonElement(it)) }
            }.toString()
            path.last() == "_stats" -> """{"indices":{"source":{"uuid":"uuid-1","shards":{"0":[{"routing":{"primary":true},"seq_no":{"max_seq_no":922337203685477580}},{"routing":{"primary":false}}]}}}}"""
            path.last() == "_search" -> """{"timed_out":${failure == "timeout"},"_shards":{"total":2,"successful":${if(failure=="shards") 1 else 2},"failed":${if(failure=="shards") 1 else 0}},"pit_id":"snapshot","hits":{"total":{"value":1,"relation":"${if(failure=="total") "gte" else "eq"}"},"hits":[{"_index":"source","_id":"storage-id","_source":{"id":"payload-id"},"sort":${if(failure == "sort") "[]" else if(openSearch) "[\"source\",\"storage-id\"]" else "[42]"}}]}}"""
            else -> error("Unexpected request")
        }
        return RestResponse.Status2XX.OK(text.encodeToByteArray())
    }
}
