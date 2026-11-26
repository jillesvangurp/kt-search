package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class PitPagingIntegrationTest : SearchTestBase() {
    @Test
    fun independentPagesKeepStorageIdentityAcrossIndicesAndSourceIdDuplicates() = coRun {
        supported {
            val indices = listOf(randomIndexName(), randomIndexName())
            val alias = randomIndexName()
            var pit: String? = null
            try {
                indices.forEach { name ->
                    client.createIndex(name) {
                        settings { shards = 2; replicas = 0 }
                        mappings { keyword("storageKey"); keyword("id") }
                    }
                    client.bulk(target = name, refresh = Refresh.WaitFor) {
                        repeat(2) { n ->
                            index(id = "stored-$n", source = """{"storageKey":"stored-$n","id":"same-payload-id"}""")
                        }
                    }
                }
                client.updateAliases { indices.forEach { addAliasForIndex(it, alias) } }
                pit = client.createPointInTime(alias, 60.seconds, false)
                val initial = client.indexStats(alias, IndexStatsLevel.Shards)
                initial.indices.size shouldBe 2
                initial.indices.values.all { it.uuid != null && it.shards.size == 2 &&
                    it.shards.values.all { copies -> copies.single { shard -> shard.routing?.primary == true }.seqNo?.maxSeqNo != null }
                } shouldBe true
                var after: JsonArray? = null
                val actual = mutableListOf<Pair<String, String>>()
                repeat(5) {
                    val response = client.searchPointInTimePage(requireNotNull(pit), 60.seconds, "storageKey", after) {
                        resultSize = 1
                    }
                    response.total shouldBe 4
                    pit = response.pitId ?: response.pointInTimeId ?: pit
                    response.searchHits.forEach { hit ->
                        hit.source?.get("id") shouldBe JsonPrimitive("same-payload-id")
                        actual.add(hit.index to hit.id)
                    }
                    after = response.searchHits.lastOrNull()?.sort ?: after
                }
                actual.size shouldBe 4
                actual.toSet() shouldBe indices.flatMap { name -> (0..1).map { name to "stored-$it" } }.toSet()
                client.indexDocument(indices.first(), """{"storageKey":"new","id":"new"}""", id = "new", refresh = Refresh.WaitFor)
                val changed = client.indexStats(alias, IndexStatsLevel.Shards)
                fun IndexStatsDetails.primarySequences() = shards.mapValues { (_, copies) ->
                    copies.single { it.routing?.primary == true }.seqNo?.maxSeqNo
                }
                (changed.indices.getValue(indices.first()).primarySequences() !=
                    initial.indices.getValue(indices.first()).primarySequences()) shouldBe true
                changed.indices.getValue(indices.last()).primarySequences() shouldBe
                    initial.indices.getValue(indices.last()).primarySequences()
                client.searchPointInTimePage(requireNotNull(pit), 60.seconds, "storageKey") { resultSize = 1 }.total shouldBe 4
                client.search(alias).total shouldBe 5
            } finally {
                pit?.let { client.deletePointInTime(it) }
                indices.forEach { client.deleteIndex(it, ignoreUnavailable = true) }
            }
        }
    }

    @Test
    fun strictPitCreationRejectsAnUnavailablePrimaryShard() = coRun {
        supported {
            val good = randomIndexName()
            val unavailable = randomIndexName()
            var partial: String? = null
            try {
                client.createIndex(good) { settings { shards = 1; replicas = 0 } }
                client.createIndex(unavailable, waitForActiveShards = 0) {
                    settings {
                        shards = 1; replicas = 0
                        this["index.routing.allocation.require._name"] = "no-such-test-node"
                    }
                }
                shouldThrow<RestException> {
                    client.createPointInTime("$good,$unavailable", 60.seconds, false)
                }
                val variant = client.engineInfo().variantInfo
                if (variant.variant == SearchEngineVariant.ES7 ||
                    (variant.variant == SearchEngineVariant.ES8 && (variant.minorVersion ?: 0) < 16)) {
                    shouldThrow<IllegalArgumentException> {
                        client.createPointInTime("$good,$unavailable", 60.seconds, true)
                    }
                } else {
                    partial = client.createPointInTime("$good,$unavailable", 60.seconds, true)
                    partial.isNullOrBlank() shouldBe false
                }
            } finally {
                partial?.let { client.deletePointInTime(it) }
                listOf(good, unavailable).forEach { client.deleteIndex(it, ignoreUnavailable = true) }
            }
        }
    }

    private suspend fun supported(block: suspend () -> Unit) = onlyOn(
        "PIT requires Elasticsearch 7+ or OpenSearch 2+",
        SearchEngineVariant.ES7, SearchEngineVariant.ES8, SearchEngineVariant.ES9,
        SearchEngineVariant.OS2, SearchEngineVariant.OS3, block = block,
    )
}
