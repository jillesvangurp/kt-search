package documentation.manual.search

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.matchPhrasePrefix
import com.jillesvangurp.searchdsls.querydsl.term
import documentation.sourceGitRepository
import kotlinx.serialization.Serializable


val searchMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))
    @Serializable
    data class TestDoc(val id: String, val name: String, val tags: List<String> = listOf())
    val indexName = "docs-search-demo"



    +"""
        Searching is of course the main reason for using Opensearch and Elasticsearch. Kt-search supports this
        with a rich Kotlin DSL. However, you can also use string literals to search.
    """.trimIndent()

    section("Some test documents") {
        +"""
            Let's quickly create some documents to search through.
        """.trimIndent()
        suspendingBlock {
            @Serializable
            data class TestDoc(val name: String, val tags: List<String> = listOf())

            val indexName = "docs-search-demo"
            // re-create the index
            client.deleteIndex(indexName)
            client.createIndex(indexName) {
                mappings { text(TestDoc::name) }
                mappings { keyword(TestDoc::tags) }
            }

            val docs = listOf(
                TestDoc(
                    id = "1",
                    name = "Apple",
                    tags = listOf("fruit")
                ),
                TestDoc(
                    id = "2",
                    name = "Banana",
                    tags = listOf("fruit")
                ),
                TestDoc(
                    id = "3",
                    name = "Beans",
                    tags = listOf("legumes")
                )
            )
            docs.forEach { d ->
                client.indexDocument(
                    target=indexName,
                    document = d,
                    id = d.id,
                    refresh = Refresh.WaitFor
                )
            }
        }
    }

    section("Searching without the Kotlin DSL") {
        +"""
            The simplest is to search for everything: 
        """.trimIndent()
        suspendingBlock {
            // will print the ids of the documents that were found
            client.search(indexName).ids

        }

        +"""
            Of course normally, you'd specify some kind of query. One way is to simply pass that as a string.
        """.trimIndent()
        suspendingBlock {
            client.search(indexName, rawJson = """
                {
                    "query": {
                        "term": {
                            "tags": {
                              "value":"legumes"
                            }
                        }
                    }
                }
            """.trimIndent()).ids
        }
    }

    section("Using the SearchDSL") {
        +"""
            Of course it is much nicer to query using the Kotlin Search DSL. Here is the same query using the SearchDSL
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                query = term(TestDoc::tags, "legumes")
            }.ids
        }

        +"""
            `client.search` takes a block that has a `SearchDSL` object as its receiver. You can use this to customize
            your query and add e.g. sorting, aggregations, queries, paging, etc. Most commonly used features are supported
            and anything that isn't supported, you can still add by using the map functionality. For example, this is how
            you would do the term query that way:
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                // you can assign maps, lists, primitives, etc.
                this["query"] =  mapOf(
                    // of course JsonDsl is just a map
                    "term" to withJsonDsl {
                        // and withJsonDsl is just short for this:
                        this[TestDoc::tags.name] = JsonDsl().apply {
                           this["value"] = "legumes"
                        }
                    }
                )
            }.ids
        }
    }
    section("Supported queries") {
        +"""
            Currently the following queries are supported:
            
            - all term level queries (term, terms, regex, etc.)
            - all full text queries (match, match_phrase_prefix, multi-match, etc.)
            - all compound queries (bool, boosting, dismax, etc.)
            - nested queries
            - some aggregation queries
            
            Adding more queries to the DSL is easy and we welcome pull requests for this.
        """.trimIndent()

        suspendingBlock {
            client.search(indexName) {
                from=0
                // size is of course also a thing in Map
                resultSize=100
                // more relevant if you have more than 10k hits
                trackTotalHits = "true" // not always a boolean in the DSL
                // a more complex query
                query = bool {
                    filter(
                        term(TestDoc::tags,"fruit")
                    )
                    should(
                        matchPhrasePrefix(TestDoc::name, "ban")
                    )
                }
            }.parseHits<TestDoc>().map { it.name }
        }

        +"""
            Note how we are parsing the hits back to TestDoc here
        """.trimIndent()
    }

    // TODO scrolling search and search_after
}