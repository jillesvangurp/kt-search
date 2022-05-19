package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.ObjectMapper
import com.jillesvangurp.searchdsls.querydsl.*
import org.elasticsearch.action.search.configure
import org.junit.jupiter.api.Test

class SearchDSLTest : AbstractElasticSearchTest(indexPrefix = "search", createIndex = true) {
    val objectMapper = ObjectMapper()

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `should construct matchAll query`() {
        val s = SearchDSL()
        s.apply {
            resultSize = 10
            from = 0
            query = matchAll()
        }
        assertThat(s["from"]).isEqualTo(0)
        assertThat(s["query"] as Map<String, Any>).hasSize(1)
        assertThat((s["query"] as Map<String, Any>).keys).contains("match_all")
    }

    @Test
    fun `should construct bool`() {
        testQuery {
            query = bool {
                should(
                    MatchQuery("title", "foo"),
                    MatchQuery("title", "quick brown fox") {
                        // ESQuery is a MutableMap that modifies the underlying queryDetails
                        this["boost"] = 0.6
                    }
                )
            }
        }
    }

    @Test
    fun `boosting query`() {
        testQuery {
            query = boosting {
                positive = matchAll()
                negative = MatchQuery("title", "nooo")
                negativeBoost = 0.1
            }
        }
    }

    @Test
    fun `match query`() {
        testQuery {
            query = MatchQuery("title", "foo bar") {
                operator = MatchOperator.AND
            }
        }
    }

    @Test
    fun `match bool prefix query`() {
        testQuery {
            query = MatchBoolPrefixQuery("title", "foo bar") {
                operator = MatchOperator.OR
            }
        }
    }

    @Test
    fun `match phrase prefix query`() {
        testQuery {
            query = MatchPhrasePrefixQuery("title", "foo ba") {
                slop = 3
            }
        }
    }

    @Test
    fun `multi match query`() {
        testQuery {
            query = MultiMatchQuery("foo bar", "title", "description") {
                type = MultiMatchType.best_fields
                fuzziness = "AUTO"
            }
        }
    }

    @Test
    fun `query string query`() {
        testQuery {
            query = QueryStringQuery("foo bar") {
                defaultField = "title"
                fuzziness = "AUTO"
            }
        }
    }

    @Test
    fun `simple query string query`() {
        testQuery {
            query = SimpleQueryStringQuery("foo AND bra", "title", "description") {
                fuzzyTranspositions = true
            }
        }
    }

    @Test
    fun `exists query`() {
        testQuery {
            query = ExistsQuery("title")
        }
    }

    @Test
    fun `fuzzy query`() {
        testQuery {
            query = FuzzyQuery("title", "ofo bra") {
                fuzziness = "AUTO"
            }
        }
    }

    @Test
    fun `ids query`() {
        testQuery {
            query = IdsQuery("1", "2")
        }
    }

    @Test
    fun `range query`() {
        testQuery {
            query = RangeQuery("Title") {
                gt = 42
            }
        }
    }

    @Test
    fun `regexp query`() {
        testQuery {
            query = RegExpQuery("title", "f.*")
        }
    }

    @Test
    fun `term query`() {
        testQuery {
            query = TermQuery("_id", "42")
        }
    }

    @Test
    fun `terms query`() {
        testQuery {
            query = TermsQuery("_id", "42", "43")
        }
    }

    @Test
    fun `wildcard`() {
        testQuery {
            query = WildCardQuery("title","1*")
        }
    }

    @Test
    fun `add sort fields`() {
        testQuery {
            sort {
                +"tag"
                +TestModel::tag
                add(TestModel::number, order = SortOrder.DESC, mode = SortMode.MAX)
            }
        }
    }

    @Test
    fun `should construct a query containing a post_filter expression`() {
        val s = SearchDSL()
        s.apply {
            resultSize = 10
            from = 0
            query = matchAll()
            postFilter = bool {
                must(
                    MatchQuery("title", "foo")
                )
            }
        }
        assertThat(s["from"]).isEqualTo(0)
        assertThat(s["query"] as Map<String, Any>).hasSize(1)
        assertThat((s["query"] as Map<String, Any>).keys).contains("match_all")
        assertThat(s["post_filter"] as Map<String, Any>).hasSize(1)
        assertThat((s["post_filter"] as Map<String, Any>).keys).contains("bool")
    }

    @Test
    fun `should construct a query containing a post_filter expression which is executed successfull`() {
        testQuery {
            query = matchAll()
            postFilter = bool {
                must(
                    MatchQuery("title", "foo")
                )
            }
        }
    }

    @Test
    fun `should construct source filter with boolean`() {
        val s = SearchDSL()
        s.apply {
            filterSource(true)
        }
        assertThat(s["_source"]).isEqualTo(true)
    }

    @Test
    fun `should construct source filter with a list of fields`() {
        val fields = arrayOf("foo", "bar")
        val s = SearchDSL()
        s.apply {
            filterSource(*fields)
        }
        assertThat(s["_source"] as Array<String>).isEqualTo(fields)
    }

    @Test
    fun `should construct source filter with include and exclude`() {
        testQuery {
            filterSource {
                includes("foo", "bar")
                excludes("baz")
            }
        }
    }

    private fun testQuery(block: SearchDSL.() -> Unit): SearchResults<TestModel> {
        // we test here that ES does not throw some kind of error and accepts the query without validation problems
        // we don't care about the results in this case
        // note we also don't test all parameters
        return repository.search {
            configure(pretty = true, block = block)
        }
    }
}
