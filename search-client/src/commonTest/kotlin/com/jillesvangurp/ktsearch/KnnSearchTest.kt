package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.querydsl.KnnQuery
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlin.test.Test

@Serializable
data class KnnTestDoc(val name: String, val vector: List<Double>)

class KnnSearchTest : SearchTestBase() {

    @Test
    fun shouldDoKnnSearch() = coRun {
        onlyOn(
            "knn only works with ES8",
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
        ) {
            val index = randomIndexName()
            client.createIndex(index) {
                mappings {
                    keyword(KnnTestDoc::name)
                    denseVector(KnnTestDoc::vector, 3, index = true)
                }
            }
            client.bulk(target = index) {
                create(KnnTestDoc("1", listOf(0.1, 0.3, 0.9)))
                create(KnnTestDoc("2", listOf(0.09, 0.31, 0.90)))
                create(KnnTestDoc("3", listOf(0.9, 0.1, 0.5)))
                create(KnnTestDoc("4", listOf(0.1, 0.1, 0.1)))
            }

            client.search(index) {
                knn = KnnQuery(KnnTestDoc::vector, listOf(0.93, 0.11, 0.48))
            }.parseHits(KnnTestDoc.serializer()).map { it.name }.let { ids ->
                ids.size shouldBe 4 // it will rank all of them
                ids.first() shouldBe "3" // 3 is the closest
            }
        }
    }
}