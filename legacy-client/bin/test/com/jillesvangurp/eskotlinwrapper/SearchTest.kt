package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.endsWith
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import com.jillesvangurp.searchdsls.querydsl.SearchType
import com.jillesvangurp.searchdsls.querydsl.filterSource
import com.jillesvangurp.searchdsls.querydsl.matchAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.search.configure
import org.elasticsearch.action.search.source
import org.elasticsearch.client.multiSearchAsync
import org.elasticsearch.core.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.junit.jupiter.api.Test

class
SearchTest : AbstractElasticSearchTest(indexPrefix = "search") {

    @Test
    fun `lets query some things`() {
        // lets put some documents in an index
        repository.bulk {
            index(randomId(), TestModel("the quick brown emu"))
            index(randomId(), TestModel("the quick brown fox"))
            index(randomId(), TestModel("the quick brown horse"))
            index(randomId(), TestModel("lorem ipsum"))
        }
        repository.refresh()

        // get SearchResults with our DSL
        val results = repository.search {
            // this is now the searchRequest, the index is already set correctly
            source(
                SearchSourceBuilder.searchSource()
                    .size(20)
                    .query(
                        BoolQueryBuilder()
                            .must(MatchQueryBuilder("message", "quick"))
                    )
            )
        }

        // we put totalHits at the top level for convenience
        assertThat(results.total).isEqualTo(results.searchResponse.hits.totalHits?.value)
        results.mappedHits.forEach {
            // and we use jackson to deserialize the results
            assertThat(it.message).contains("quick")
        }
        // iterating twice is no problem
        results.mappedHits.forEach {
            assertThat(it.message).contains("quick")
        }
    }

    @Test
    fun `you can search by pasting json as a String from the Kibana dev console as well`() {
        repository.bulk {
            index(randomId(), TestModel("the quick brown emu"))
            index(randomId(), TestModel("the quick brown fox"))
            index(randomId(), TestModel("the quick brown horse"))
            index(randomId(), TestModel("lorem ipsum"))
        }
        repository.refresh()
        val keyWord = "quick"
        val results = repository.search {
            // sometimes it is nice to just paste a query you prototyped in the developer console
            // also, Kotlin has multi line strings so this stuff is actually readable
            // and you can use variables in them!
            source(
                """
{
  "size": 20,
  "query": {
    "match": {
      "message": "$keyWord"
    }
  }
}
            """
            )
        }
        assertThat(results.total).isEqualTo(results.searchResponse.hits.totalHits?.value)
        results.mappedHits.forEach {
            // and we use jackson to deserialize the results
            assertThat(it.message).contains(keyWord)
        }
    }

    @Test
    fun `scrolling searches are easy`() {
        // lets put some documents in an index
        repository.bulk {
            for (i in 1..103) {
                index(randomId(), TestModel("doc $i"))
            }
        }
        repository.refresh()

        val results = repository.search {
            scroll(TimeValue.timeValueMinutes(1L))
            source(
                SearchSourceBuilder()
                    .query(matchAllQuery())
                    .size(5)
            )
        }

        val fetchedResultsSize = results.mappedHits.count().toLong()
        assertThat(fetchedResultsSize).isEqualTo(results.total)
        assertThat(fetchedResultsSize).isEqualTo(103L)
    }

    @Test
    fun `scroll and bulk update works too`() {
        repository.bulk {
            for (i in 1..19) {
                index(randomId(), TestModel("doc $i"))
            }
        }
        repository.refresh()

        val queryForAll = SearchSourceBuilder()
            .query(matchAllQuery())
            // we need the version so that we can do bulk updates
            .seqNoAndPrimaryTerm(true)
            .size(5)
        val results = repository.search {
            scroll(TimeValue.timeValueMinutes(1L))
            source(queryForAll)
        }

        repository.bulk {
            results.hits.forEach { (searcHit, mapped) ->
                update(searcHit.id, searcHit.seqNo, searcHit.primaryTerm, mapped!!) {
                    it.message = "${it.message} updated"
                    it
                }
            }
        }

        repository.refresh()

        val updatedResults = repository.search {
            scroll(TimeValue.timeValueMinutes(1L))
            source(queryForAll)
        }
        assertThat(updatedResults.total).isEqualTo(19L)
        updatedResults.mappedHits.forEach {
            assertThat(it.message).endsWith("updated")
        }
    }

    @Test
    fun `should do msearch`() {
        runBlocking {
            val response = asyncRepository.jsonMSearch("""
                {}
                { "query":{ "match_all": {}}}
                {}
                { "query":{ "match_all": {}}}
                
            """.trimIndent())
            assertThat(response.responses.size).isEqualTo(2)
        }
    }

    @Test
    fun `should user multisearch dsl`() {
        runBlocking {
            val results = asyncRepository.mSearch {
                header {
                    searchType = SearchType.query_then_fetch
                } withQuery {
                    query = matchAll()
                }
                header {
                    searchType = SearchType.dfs_query_then_fetch
                } withQuery {
                    query = matchAll()
                }
            }

            assertThat(results.responses.size).isEqualTo(2)
        }
    }

    @Test
    fun `should handle source filtering`() {
        runBlocking {
            asyncRepository.index("xxx", TestModel("foo bar", "tt",42.0))
            asyncRepository.refresh()
            val msg = asyncRepository.search {
                configure {
                    this["fields"] = listOf(TestModel::message.name)
                }
            }.searchHits.map {
                it.field(TestModel::message.name).values.first()
            }.first()

            assertThat(msg).isEqualTo("foo bar")
        }
    }
}
