package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.searchdsls.SearchEngineVariant
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SearchAfterLifecycleIntegrationTest : SearchTestBase() {
    @Test
    fun consumedAndTruncatedPagingReleaseTheirRealSnapshots() = coRun {
        onlyOn(
            "PIT requires Elasticsearch 7+ or OpenSearch 2+",
            SearchEngineVariant.ES7, SearchEngineVariant.ES8, SearchEngineVariant.ES9,
            SearchEngineVariant.OS2, SearchEngineVariant.OS3,
        ) {
            val index = randomIndexName()
            try {
                client.createIndex(index) { settings { shards = 1; replicas = 0 } }
                client.bulk(target = index, refresh = Refresh.WaitFor) {
                    repeat(2) { index(id = "document-$it", source = "{}") }
                }
                for (truncate in listOf(false, true)) {
                    val (response, hits) = client.searchAfter(index, 60.seconds) { resultSize = 1 }
                    val pit = requireNotNull(response.pitId ?: response.pointInTimeId)
                    try {
                        suspend fun searchSnapshot() = client.search(null) {
                            this["pit"] = withJsonDsl { this["id"] = pit }
                        }
                        // Prove the same request succeeds while the managed snapshot is open.
                        searchSnapshot().total shouldBe 2
                        val ids = (if (truncate) hits.take(1) else hits).map { it.id }.toList()
                        ids.size shouldBe if (truncate) 1 else 2
                        shouldThrow<RestException> { searchSnapshot() }
                    } finally {
                        // A failing regression must not leave its fixture snapshot behind.
                        try {
                            client.deletePointInTime(pit)
                        } catch (failure: RestException) {
                            if (failure.status != 404) throw failure
                        }
                    }
                }
            } finally {
                client.deleteIndex(index, ignoreUnavailable = true)
            }
        }
    }
}
