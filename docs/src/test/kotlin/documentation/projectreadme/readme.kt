@file:Suppress("UNUSED_VARIABLE")

package documentation.projectreadme

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.matchPhrasePrefix
import com.jillesvangurp.searchdsls.querydsl.term
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

val projectReadme = sourceGitRepository.md {
    +"""
[![matrix-test-and-deploy-docs](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)](https://github.com/jillesvangurp/kt-search/actions/workflows/deploy-docs-and-test.yml)

Kt-search is a Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem. It provides Kotlin DSLs for querying, defining mappings,  bulk indexing, and more. 

Integrate advanced search in your Kotlin applications. Whether you are building a web based dashboard, an advanced ETL pipeline, or simply exposing a search endpoint in as a microservice, this library has you covered. You can also integrate kt-search into your `kts` scripts. For this we have a little companion library to get you started: [kt-search-kts](https://github.com/jillesvangurp/kt-search-kts/). Also, see the scripting section in the manual.

Because it is a multi platform library you can embed it in your server (Ktor, Spring Boot, Quarkus), use it in a browser using kotlin-js, or embed it in your Android/IOS apps. For this, it relies on all the latest and greatest multi-platform Kotlin features that you love: co-routines, kotlinx.serialization, ktor-client 2.x., etc., whic work across all these platforms.

The goal for kt-search is to be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.  
      
It is extensible and modular. You can easily add your own custom DSLs for e.g. things not covered by this library or custom plugins you use. And while it is opinionated about using e.g. kotlinx.serialization, you can also choose to use alternative serialization frameworks, or even use your own http client and just use the search-dsl.
    """.trimIndent()

    includeMdFile("gradle.md")

    includeMdFile("readme-intro.md")
    section("Usage") {
        // our test server runs on port 9999
        val client = SearchClient(
            KtorRestClient(
                // our test server runs on port 9999
                port = 9999
            )
        )

        @Serializable
        data class TestDocument(
            val name: String,
            val tags: List<String>? = null
        )


        // this is what we show in the Readme :-)
        block {
            val client = SearchClient(
                KtorRestClient()
            )
        }

        +"""
            First create a client. Kotlin has default variables for parameters. So, we use sensible defaults. This 
            is something we do wherever we can in this library. 
        """.trimIndent()


        block() {
            @Serializable
            data class TestDocument(
                val name: String,
                val tags: List<String>? = null
            )
        }

        +"""
            In the example below we will use this `TestDocument`, which we can serialize using the kotlinx.serialization framework.
        """.trimIndent()

        block {
            //
            val indexName = "index-${Clock.System.now().toEpochMilliseconds()}"

            // create a co-routine context, kt-search uses `suspend` functions
            runBlocking {
                // create an index and use our mappings dsl
                client.createIndex(indexName) {
                    settings {
                        replicas = 0
                        shards = 3
                    }
                    mappings(dynamicEnabled = false) {
                        text(TestDocument::name)
                        keyword(TestDocument::tags)
                    }
                }

                // bulk index some documents
                client.bulk(refresh = Refresh.WaitFor) {
                    index(
                        doc = TestDocument(
                            name = "apple",
                            tags = listOf("fruit")
                        ),
                        index = indexName
                    )
                    index(
                        doc = TestDocument(
                            name = "orange",
                            tags = listOf("fruit", "citrus")
                        ),
                        index = indexName,
                    )
                    index(
                        // you can also provide raw json
                        source = DEFAULT_JSON.encodeToString(
                            TestDocument.serializer(),
                            TestDocument(
                                name = "banana",
                                tags = listOf("fruit", "tropical")
                            )),
                        index = indexName
                    )
                }

                // search
                val results = client.search(indexName) {
                    query = bool {
                        must(
                            term(TestDocument::tags, "fruit"),
                            matchPhrasePrefix(TestDocument::name, "app")
                        )
                    }
                }

                println("found ${results.total} hits")
                results
                    // extension function that deserializes
                    // uses kotlinx.serialization
                    // but you can use something else
                    .parseHits<TestDocument>()
                    .first()
                    // hits don't always include source
                    // in that case it will be a null document
                    ?.let {
                        println("doc ${it.name}")
                    }
                // you can also get the JsonObject if you don't
                // have a model class
                println(results.hits?.hits?.first()?.source)
            }
        }

        +"""
            This example shows off a few nice features of this library:
            
            - There is a convenient mapping and settings DSL that you can use to create indices
            - In te mappings and in your queries, you can use kotlin property references instead of
            field names.
            - Bulk indexing does not require any bookkeeping with kt-search. The `bulk` block
            creates a `BulkSession` for you and it deals with sending bulk requests and picking
            the responses apart. BulkSession has a lot of optional featured that you can use: 
            it has item callbacks, you can specify the refresh parameter, you can make it 
            fail on the first item failure, etc. Alternatively, you can make it robust against
            failures, implement error handling and retries, etc.
            - You can use kotlinx.serialization for your documents but you don't have to. 
            Note how you can pass in a json string for parsing and how the search response
            returns a JsonObject, which we then decode. 
            
            For more details, refer to the manual.
        """.trimIndent()
    }


    includeMdFile("readme-outro.md")
}
