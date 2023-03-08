package documentation.manual.search

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.manual.ManualPages
import documentation.mdLink
import documentation.sourceGitRepository
import kotlinx.serialization.Serializable


val searchMd = sourceGitRepository.md {
    val indexName = "docs-search-demo"
    client.indexTestFixture(indexName)

    +"""
        Searching is of course the main reason for using Opensearch and Elasticsearch. Kt-search supports this
        with a rich Kotlin DSL. However, you can also use string literals to search.
        
        The advantage of using a Kotlin DSL for writing your queries is that you can rely on Kotlin's type safety
        and also use things like refactoring, property references to fields in your model classes, functional programming,
        etc.
                
    """.trimIndent()

    section("Some test documents") {
        +"""
            Let's quickly create some documents to search through.
        """.trimIndent()
        suspendingBlock {
            @Serializable
            data class TestDoc(val name: String, val tags: List<String> = listOf())
        }

        snippetFromSourceFile("documentation/manual/search/helpers.kt", "INITTESTFIXTURE")
        +"""
            This creates a simple index with a custom mapping and adds some documents using our API.
            
            You can learn more about creating indices with customized mappings here: ${ManualPages.IndexManagement.page.mdLink}
        """.trimIndent()
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
            The `ids` extension property, extracts a list of ids from the hits in the response.
            
            Of course normally, you'd specify some kind of query. One valid way is to simply pass that as a string.
            Kotlin of course has multiline strings that can be templated as well. So, this may be all you need.
        """.trimIndent()
        suspendingBlock {
            val term = "legumes"
            client.search(
                indexName, rawJson = """
                {
                    "query": {
                        "term": {
                            // using property references is a good idea
                            "tags": {                             
                              "value":"$term"
                            }
                        }
                    }
                }
            """.trimIndent()
            ).ids
        }
    }

    +"""
        With some hand crafted queries, this style of querying may be useful. Another advantage is that 
        you can paste queries straight from the Kibana development console.
    """.trimIndent()

    section("Using the SearchDSL") {
        +"""
            Of course it is nicer to query using a Kotlin Search DSL (Domain Specific Language). 
            Here is the same query using the `SearchDSL`.
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                query = term(TestDoc::tags, "legumes")
            }.ids
        }

        +"""
        """.trimIndent()
        +"""
            The `client.search` function takes a block that has a `SearchDSL` object as its receiver. You can use this to customize
            your query and add e.g. sorting, aggregations, queries, paging, etc.  
            
            The following sections describe (most) of the query dsl:
            
            - ${ManualPages.TextQueries.page.mdLink}
            - ${ManualPages.TermLevelQueries.page.mdLink}
            - ${ManualPages.CompoundQueries.page.mdLink}
            - ${ManualPages.Aggregations.page.mdLink}
            - ${ManualPages.DeepPaging.page.mdLink}
            
            Most commonly used query types are supported
            and anything that isn't supported, you can still add by using the map functionality.          
            For example, this is how you would construct the term query using the underlying map:
        """.trimIndent()
        suspendingBlock {
            client.search(indexName) {
                // you can assign maps, lists, primitives, etc.
                this["query"] = mapOf(
                    // JsonDsl is what the SearchDsl uses as its basis.
                    "term" to withJsonDsl {
                        // and withJsonDsl is just short for this:
                        this[TestDoc::tags.name] = JsonDsl().apply {
                            this["value"] = "legumes"
                        }
                        // the advantage over a map is more flexible put functions
                    }
                )
            }.ids
        }

        +"""
            For more information on how to extend the DSL or how to create your own DSL see ${ManualPages.ExtendingTheDSL.page.mdLink}
        """.trimIndent()


        section("Picking apart the results") {
            +"""
                Of course a key reason for querying is to get the documents you indexed back and 
                deserializing those back to your model classes so that you can do things with them.
                
                Here is a more complex query that returns fruit with `ban` as the name prefix.
            """.trimIndent()
            suspendingBlock {
                val resp = client.search(indexName) {
                    from = 0
                    // size is of course also a thing in Map
                    resultSize = 100
                    // more relevant if you have more than 10k hits
                    trackTotalHits = "true" // not always a boolean in the DSL
                    // a more complex bool query
                    query = bool {
                        filter(
                            term(TestDoc::tags, "fruit")
                        )
                        should(
                            matchPhrasePrefix(TestDoc::name, "ban")
                        )
                    }
                }
                // deserializes all the hits and extract the name
                val names = resp.parseHits<TestDoc>().map { it.name }

                println(names.joinToString("\n"))

                // you can also parse individual hits:
                resp.hits?.hits?.forEach { hit ->
                    val doc = hit.parseHit<TestDoc>()
                    println("${hit.id} - ${hit.score}: ${doc.name} (${doc.price})")
                }
            }

            +"""
                By default, the source gets deserialized as a `JsonObject`. However, with `kotlinx.serialization`, you can
                use that as the input for `decodeFromJsonElement<T>(object)` to deserialize to some custom
                data structure. This is something we use in multiple places and it gives us the flexibility to
                be schema less when we need to and use a rich model when want to.
            """.trimIndent()

        }
    }
    section("Count API") {
        +"""
            Elasticsearch also has a more limited _count API dedicated to simply counting results.
        """.trimIndent()
        suspendingBlock {
            // count all documents
            println("Number of docs" + client.count(indexName).count)
            // or with a query
            println("Number of docs" + client.count(indexName) {
                query = term(TestDoc::tags, "fruit")
            }.count)
        }
    }
    section("Multi Search") {
        +"""
            Kt-search also includes DSL support for doing multi searches. The msearch API has a similar API as the 
            bulk API in the sense that it takes a body that has headers and search requests interleaved. Each line
            of the body is a json object. Likewise, the response is in ndjson form and contains a search response
            for each of the search requests.
            
            The msearch DSL in kt-search makes this very easy to do:
        """.trimIndent()

        suspendingBlock {
            client.msearch(indexName) {
                // the header is optional, this will simply add
                // {} as the header
                add {
                    // the full search dsl is supported here
                    // will query indexName for everything
                    query = matchAll()
                }
                add(msearchHeader {
                    // overrides the indexName and queries
                    // everything in the cluster
                    allowNoIndices = true
                    index = "*"
                }) {
                    from = 0
                    resultSize = 100
                    query = matchAll()
                }
            }.responses.forEach { searchResponse ->
                // will print document count for both searches
                println("document count ${searchResponse.total}")
            }
        }
        +"""
            Similar to the normal search, you can also construct your body manually. The format is ndjson
        """.trimIndent()

        suspendingBlock {
            val resp = client.msearch(
                body = """
                {"index":"$indexName"}
                {"query":{"match_all":{}}}
                
                """.trimIndent() // the extra new line is required by ES
            )
            println("Doc counts: ${
                resp.responses.joinToString { it.total.toString() }
            }")
        }
    }
}