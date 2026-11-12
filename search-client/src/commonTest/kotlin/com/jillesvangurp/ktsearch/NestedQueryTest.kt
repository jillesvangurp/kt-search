package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import com.jillesvangurp.searchdsls.querydsl.NestedAgg
import com.jillesvangurp.searchdsls.querydsl.ReverseNestedAgg
import com.jillesvangurp.searchdsls.querydsl.ScoreMode
import com.jillesvangurp.searchdsls.querydsl.SumAgg
import com.jillesvangurp.searchdsls.querydsl.agg
import com.jillesvangurp.searchdsls.querydsl.nested
import com.jillesvangurp.searchdsls.querydsl.range
import com.jillesvangurp.serializationext.DEFAULT_JSON
import com.jillesvangurp.serializationext.DEFAULT_PRETTY_JSON
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test

class NestedQueryTest : SearchTestBase() {
    @Test
    fun shouldCreatedMappingWithNestedFieldAndQuery() = coRun {
        val index = randomIndexName()
        client.createIndex(index, NestedTestDocument.mapping)
        println(NestedTestDocument.mapping)

        val repo = client.repository(index,NestedTestDocument.serializer())

        repo.index(NestedTestDocument("1", listOf(
            TestDocument("1.1", number=1),
            TestDocument("1.2", number=2),
            TestDocument("1.3", number=3),
        )))
        repo.index(NestedTestDocument("2", listOf(
            TestDocument("2.3", number=3),
            TestDocument("2.4", number=4),
        )))

        val r0 = repo.search {  }
        r0.total shouldBe 2
        val r1 = repo.search {
            query = nested {
                path = "test_docs"
                query = range("test_docs.number") {
                    gt = 3
                    lt = 5
                }
                scoreMode = ScoreMode.none
                ignoreUnmapped = true
            }
        }
        r1.total shouldBe 1

        val response = repo.search {
            resultSize = 0
            agg("children", NestedAgg("test_docs")) {
                agg("total", SumAgg("test_docs.number"))
                agg("parents", ReverseNestedAgg())
            }
        }
        val children = requireNotNull(response.aggregations.filterResult("children"))
        children.docCount shouldBe 5L
        children.bucket.sumAggregationResult("total").value shouldBe 13.0
        children.bucket.getAggResult<FilterAggregationResult>("parents").docCount shouldBe 2L
    }
}


@Serializable
data class NestedTestDocument(
    val name: String,
    @SerialName("test_docs")
    val testDocs: List<TestDocument>
) {
    companion object {
        val mapping = IndexSettingsAndMappingsDSL().apply {
            mappings(dynamicEnabled = false) {
                text(NestedTestDocument::name)
                nestedField("test_docs") {
                    text(TestDocument::name)
                    number<Long>(TestDocument::number)
                }
            }
        }
    }

    @Suppress("unused")
    fun json(pretty: Boolean = false): String {
        return if (pretty)
            DEFAULT_PRETTY_JSON.encodeToString(serializer(), this)
        else
            DEFAULT_JSON.encodeToString(serializer(), this)
    }
}
