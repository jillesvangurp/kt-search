package documentation.manual.search

import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking

val textQueriesMd = sourceGitRepository.md {
    val indexName = "docs-text-queries-demo"

    runBlocking {
        client.indexTestFixture(indexName)
    }

    +"""
        If you are doing textual search, Elasticsearch offers a lot of functionality out of the box. We'll cover only
        the basics here. Please refer to the Opensearch and Elasticsearch manuals for full coverage of all the options 
        and parameters.
    """.trimIndent()

    section("Match") {
        suspendingBlock {
            client.search(indexName) {
                // will match on beans
                query = match(TestDoc::name, "red beans") {
                    boost = 2.0
                    lenient = true
                    autoGenerateSynonymsPhraseQuery = true
                }
            }.pretty("Match query").let {
                println(it)
            }
        }
    }
    section("Match Phrase") {
        suspendingBlock {
            client.search(indexName) {
                // will match on "green beans"
                query = matchPhrase(TestDoc::name, "green beans") {
                    boost = 2.0
                    slop = 2
                    zeroTermsQuery = ZeroTermsQuery.none
                }
            }.pretty("Match Phrase query").let {
                println(it)
            }
        }
    }
    section("Match Phrase Prefix") {
        suspendingBlock {
            client.search(indexName) {
                // will match on "green beans"
                query = matchPhrasePrefix(TestDoc::name, "green bea") {
                    boost = 2.0
                    slop = 2
                    zeroTermsQuery = ZeroTermsQuery.none
                }
            }.pretty("Match Phrase Prefix query").let {
                println(it)
            }
        }
    }
    section("Multi Match") {
        suspendingBlock {
            client.search(indexName) {
                // will match on "green beans"
                query = multiMatch("banana beans",
                    "name", "tags.txt") {
                    type = MultiMatchType.best_fields
                    tieBreaker = 0.3
                    operator = MatchOperator.OR
                }
            }.pretty("Multi Match").let {
                println(it)
            }
        }
    }

    section("Simple Query String") {
        +"""
            A simple query string parser that can query multiple fields
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                query = simpleQueryString( "beans OR fruit", "name", "tags.txt" )
            }.pretty("Multi Match").let {
                println(it)
            }
        }
    }

    section("Query String Query") {
        +"""
            Similar to simple query string but with a more strict query language and less leniency.
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                query = queryString( "(banana) OR (apple)", TestDoc::name)
            }.pretty("Multi Match").let {
                println(it)
            }
        }
    }
    section("Intervals query") {
        // FIXME not implemented yet
    }
    section("Combined fields query") {
        // FIXME not implemented yet
    }
}