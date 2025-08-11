package documentation.manual.search

import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.printStdOut
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
        example {
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
        }.printStdOut(this)
    }
    section("Match Phrase") {
        example {
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
        }.printStdOut(this)
    }
    section("Match Phrase Prefix") {
        example {
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
        }.printStdOut(this)
    }
    section("Multi Match") {
        example {
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
        }.printStdOut(this)
    }

    section("Simple Query String") {
        +"""
            A simple query string parser that can query multiple fields
        """.trimIndent()
        example {
            client.search(indexName) {
                query = simpleQueryString( "beans OR fruit", "name", "tags.txt" )
            }.pretty("Multi Match").let {
                println(it)
            }
        }.printStdOut(this)
    }

    section("Query String Query") {
        +"""
            Similar to simple query string but with a more strict query language and less leniency.
        """.trimIndent()
        example {
            client.search(indexName) {
                query = queryString( "(banana) OR (apple)", TestDoc::name)
            }.pretty("Multi Match").let {
                println(it)
            }
        }.printStdOut(this)
    }
    section("Intervals query") {
        +"""
            The intervals query is a powerful but also complicated query. Be sure to refer to the Elasticsearch
            manual. It allows you to query for terms using interval based match rules. These rules can get
            quite complicated and you need to be careful with how they interact. For example the order matters
            and the most minimal interval "wins". 
            
            Here is a simple example
        """.trimIndent()
        example {
            client.search(indexName) {
                query = intervals("name") {
                    matchRule {
                        query="green beans"
                        maxGaps = 1
                    }
                }
            }.pretty("Combined fields").let {
                println(it)
            }
        }.printStdOut(this)
        +"""
            You can combine multiple rules with `any_of`, or `all_of`.
        """.trimIndent()
        example {
            client.search(indexName) {
                query = intervals("name") {
                    allOfRule {
                        intervals(
                            matchRule {
                                query = "green beans"
                                maxGaps = 1
                                withFilter {
                                    notContaining(matchRule { query="red" })
                                }
                            },
                            prefixRule {
                                prefix = "gr"
                            }
                        )
                    }
                }
            }.pretty("Combined fields").let {
                println(it)
            }
        }.printStdOut(this)
    }
    section("Combined fields query") {
        example {
            client.search(indexName) {
                query = combinedFields( "banana fruit", "name^2","tags.txt") {
                    operator = MatchOperator.AND
                }
            }.pretty("Combined fields").let {
                println(it)
            }
        }.printStdOut(this)
    }
}