package com.jillesvangurp.ktsearch

import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.SearchEngineVariant
import com.jillesvangurp.searchdsls.querydsl.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.flow.count
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class SearchTest : SearchTestBase() {

    @Test
    fun shouldSearchAndCount() = coRun {
        testDocumentIndex { index ->
            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor)
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor)
            val response = client.search(index, "")
            response.total shouldBe 2

            client.search(index) {
                trackTotalHits = "true"
                query = match(TestDocument::name, "bar")
            }.total shouldBe 1

            client.count(index, MatchQuery(TestDocument::name.name, "bar")).count shouldBe 1
            client.count(index).count shouldBe 2
            client.count(index) {
                query = match(TestDocument::name, "bar")
            }.count shouldBe 1
        }
    }

    @Test
    fun shouldDoIdSearch() = coRun {
        testDocumentIndex { index ->

            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
            client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

            client.search("$index,$index") {
                query = ids("1", "3")
            }.total shouldBe 2
        }
    }

    @Test
    fun shouldReturnAllDocumentsWithMatchAll() = coRun {
        testDocumentIndex { index ->

            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
            client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

            val result = client.search("$index,$index") {
                query = constructQueryClause {
                    matchAll(boost = 3.5)
                }
            }
            result.hits!!.hits shouldHaveSize 3
            result.hits!!.hits.map(SearchResponse.Hit::score) shouldBe listOf(3.5, 3.5, 3.5)
        }
    }

    @Test
    fun shouldReturnEmptyResultWithMatchNone() = coRun {
        testDocumentIndex { index ->

            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
            client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

            val result = client.search("$index,$index") {
                query = matchNone()
            }
            result.total shouldBe 0
            result.hits!!.hits shouldHaveSize 0
        }
    }

    @Test
    fun shouldDoScrollingSearch() = coRun {
        testDocumentIndex { index ->

            client.bulk(target = index, refresh = Refresh.WaitFor) {
                (1..20).forEach {
                    index(TestDocument("doc $it").json())
                }
            }
            val resp = client.search(index, scroll = "1m") {
                resultSize = 3
                query = matchAll()
            }
            client.scroll(resp).count() shouldBe 20
        }
    }

    @Test
    fun shouldDoSearchAfter() = coRun {
        onlyOn(
            "opensearch implemented search_after with v2 later changed their _pit API",
            SearchEngineVariant.ES7,
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
            SearchEngineVariant.OS2,
            SearchEngineVariant.OS3,
        ) {
            testDocumentIndex { index ->

                client.bulk(target = index, refresh = Refresh.WaitFor) {
                    (1..200).forEach {
                        index(TestDocument("doc $it").json())
                    }
                }

                val (resp, hits) = client.searchAfter(index, 10.seconds) {
                    resultSize = 3
                    query = matchAll()
                }
                resp.total shouldBe 200
                hits.count() shouldBe 200
            }
        }
    }

    @Test
    fun shouldAllowCustomSortOnlyWithOptIn() = coRun {
        onlyOn(
            "opensearch implemented search_after with v2 but then later changed their _pit API",
            SearchEngineVariant.ES7,
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
            SearchEngineVariant.OS2,
            SearchEngineVariant.OS3,
        ) {
            testDocumentIndex { index ->

                val repo = client.repository(index, TestDocument.serializer())

                repo.bulk {
                    (1..200).forEach {
                        index(TestDocument("doc $it").json())
                    }
                }
                repo.searchAfter {
                    resultSize = 3
                    query = matchAll()
                    // no sort should add the implicit sort on _shard_doc
                }

                shouldThrow<Exception> {
                    repo.searchAfter {
                        resultSize = 3
                        query = matchAll()
                        sort {
                            // this would break search_after because tags are not unique
                            // so we require an opt-in
                            add(TestDocument::tags)
                        }
                    }
                }
                val (res, hits) = repo.searchAfter(optInToCustomSort = true) {
                    resultSize = 3
                    query = matchAll()
                    sort {
                        // be very careful with sorting, sorting on tags breaks search_after
                        // sorting on id works because it is unique
                        // sorting on the implicit _shard_doc works too and is what search_after does otherwise and probably what youb want
                        // hence the required opt-in
                        add(TestDocument::id)
                    }
                }
                res.total shouldBe 200
                hits.count() shouldBe res.total

            }
        }
    }

    @Test
    fun shouldWorkWithoutTotalHits() = coRun {
        testDocumentIndex { index ->
            client.search(target = index, trackTotalHits = false)
        }
    }

    @Test
    fun shouldCollapseResults() = coRun {
        testDocumentIndex { index ->

            client.bulk(target = index, refresh = Refresh.WaitFor) {
                index(TestDocument("doc 1", tags = listOf("group1")).json())
                index(TestDocument("doc 2", tags = listOf("group1")).json())
                index(TestDocument("doc 3", tags = listOf("group2")).json())
            }
            val results = client.search(target = index) {
                collapse(TestDocument::tags) {
                    innerHits("by_tag") {
                        resultSize = 4
                    }
                }
            }
            results.parseHits<TestDocument>().size shouldBe 2
            results.hits?.hits?.forEach { hit ->
                hit.innerHits shouldNotBe null
                hit.innerHits?.get("by_tag") shouldNotBe null
                // convoluted response json from Elasticsearch here
                hit.innerHits?.get("by_tag")?.hits?.hits?.size!! shouldBeGreaterThan 0
            }
        }
    }

    @Test
    fun msearchTest() = coRun {
        testDocumentIndex { indexName ->

            client.bulk(target = indexName, refresh = Refresh.WaitFor) {
                index(TestDocument("doc 1", tags = listOf("group1")).json())
                index(TestDocument("doc 2", tags = listOf("group1")).json())
                index(TestDocument("doc 3", tags = listOf("group2")).json())
            }
            val response = client.msearch(indexName) {
                add {
                    from = 0
                    resultSize = 100
                    query = matchAll()
                }
                add(msearchHeader {
                    allowNoIndices = true
                    index = "*"
                }) {
                    resultSize = 0
                    trackTotalHits = "true"
                }
            }
            response.responses shouldHaveSize 2
        }
    }

    @Test
    fun shouldApplyRescore() = coRun {
        testDocumentIndex { indexName ->

            client.bulk(target = indexName, refresh = Refresh.WaitFor) {
                index(TestDocument("doc 1", tags = listOf("rescore")).json())
                index(TestDocument("doc 2", tags = listOf("nope")).json())
                index(TestDocument("doc 3", tags = listOf("another")).json())
            }
            val response = client.search(indexName, explain = true) {
                query = matchAll()
                val firstRescoreQuery = constantScore {
                    filter = match(TestDocument::tags, "rescore")
                }

                val secondRescoreQuery = constantScore {
                    filter = match(TestDocument::tags, "another")
                }
                rescore(
                    rescorer(3) {
                        scoreMode = RescoreScoreMode.total
                        rescoreQueryWeight = 20.0
                        queryWeight = 2.0
                        rescoreQuery = firstRescoreQuery
                    },
                    rescorer(3) {
                        scoreMode = RescoreScoreMode.multiply
                        rescoreQueryWeight = 5.0
                        queryWeight = 1.0
                        rescoreQuery = secondRescoreQuery
                    }
                )
            }

            response.hits!!.hits shouldHaveSize 3
            response.parseHits<TestDocument>().map(TestDocument::name) shouldBe listOf("doc 1", "doc 3", "doc 2")
            response.hits!!.hits.map(SearchResponse.Hit::score) shouldBe listOf(22.0, 10.0, 2.0)
        }
    }

    @Test
    fun shouldExposeSeqNo() = coRun {
        testDocumentIndex { indexName ->

            client.bulk(target = indexName, refresh = Refresh.WaitFor) {
                index(TestDocument("doc 1", tags = listOf("group1")).json())
                index(TestDocument("doc 2", tags = listOf("group1")).json())
                index(TestDocument("doc 3", tags = listOf("group2")).json())
            }
            client.search(indexName) {
                seqNoPrimaryTerm = true
                version = true
            }.hits!!.hits.forEach { hit ->
                hit.primaryTerm shouldBe 1
                hit.seqNo shouldNotBe null
                hit.version shouldBe 1
            }
        }
    }

    @Test
    fun shouldHighlightResults() = coRun {
        testDocumentIndex { index ->

            client.bulk(target = index, refresh = Refresh.WaitFor) {
                index(TestDocument(id = 1, name = "bar", description = "another bar value").json())
                index(TestDocument(id = 2, name = "foo", description = "another foo").json())
                index(TestDocument(id = 3, name = "bar", description = "just foo").json())
            }
            val results = client.search(target = index) {
                query = match(TestDocument::name, "bar")
                sort {
                    add(TestDocument::id, SortOrder.ASC)
                }
                highlight {
                    preTags = "<b>"
                    postTags = "</b>"
                    add(TestDocument::description.name) {
                        highlightQuery = match(TestDocument::description, "bar")
                    }

                }
            }
            results.parseHits<TestDocument>().size shouldBe 2
            val hits = results.hits?.hits!!

            val highlight = hits[0].highlight?.parse<Map<String, List<String>>>()
            highlight shouldNotBe null
            highlight!![TestDocument::description.name] shouldBe listOf("another <b>bar</b> value")

            hits[1].highlight shouldBe null
        }
    }

    @Test
    fun shouldReturnMatchedNamedQueries() = coRun {
        testDocumentIndex { index ->

            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
            client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

            val result = client.search("$index,$index") {
                query = match(TestDocument::name, "bar") {
                    put("_name", "name_filter")
                }
            }
            result.hits!!.hits shouldHaveSize 2
            result.hits!!.hits.map { it.matchedQueries.names() } shouldBe listOf(listOf("name_filter"), listOf("name_filter"))
        }
    }

    @Test
    fun shouldReturnMatchedNamedQueriesWithScores() = coRun {
        onlyOn(
            "OS1 and ES7 don't support include_named_queries_score",
            SearchEngineVariant.OS2,
            SearchEngineVariant.OS3,
            SearchEngineVariant.ES8,
            SearchEngineVariant.ES9,
        ) {
            testDocumentIndex { index ->

                client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
                client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
                client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

                val result =
                    client.search("$index,$index", include_named_queries_score = true) {
                        query = match(TestDocument::name, "bar") {
                            put("_name", "name_filter")
                        }
                    }
                result.hits!!.hits shouldHaveSize 2
                val scoreByQueryName = result.hits!!.hits.first().matchedQueries.scoreByName()
                scoreByQueryName.size shouldBe 1
                scoreByQueryName.containsKey("name_filter") shouldBe true
            }
        }
    }

    @Test
    fun shouldReturnEmptyMatchedNamedQueriesIfRequestHasNoNamedQueries() = coRun {
        testDocumentIndex { index ->

            client.indexDocument(index, TestDocument("foo bar").json(false), refresh = Refresh.WaitFor, id = "1")
            client.indexDocument(index, TestDocument("fooo").json(false), refresh = Refresh.WaitFor, id = "2")
            client.indexDocument(index, TestDocument("bar").json(false), refresh = Refresh.WaitFor, id = "3")

            val result = client.search("$index,$index") {
                query = match(TestDocument::name, "bar")
            }
            result.hits!!.hits shouldHaveSize 2
            result.hits!!.hits.map { it.matchedQueries.names() } shouldBe listOf(listOf(), listOf())
            result.hits!!.hits.map { it.matchedQueries.scoreByName() } shouldBe listOf(mapOf(), mapOf())
        }
    }
}
