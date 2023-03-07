@file:Suppress("UNUSED_VARIABLE", "NAME_SHADOWING", "unused")

package documentation.projectreadme

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.bool
import com.jillesvangurp.searchdsls.querydsl.matchPhrasePrefix
import com.jillesvangurp.searchdsls.querydsl.term
import documentation.githubLink
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

val projectReadme = sourceGitRepository.md {
    +"""
[![matrix-test-and-deploy-docs]($githubLink/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)]($githubLink/actions/workflows/deploy-docs-and-test.yml)
    """.trimIndent()
    includeMdFile("oneliner.md")

    includeMdFile("readme-intro.md")

    includeMdFile("gradle.md")

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
            First create a client. Kotlin has default values for parameters. So, we use sensible defaults for the 
            `host` and `port` variables to connect to `localhohst` and `9200`. You can also configure multiple hosts, 
            or add ssl and basic authentication to connect to managed Opensearch or Elasticsearch clusters. If you use
            multiple hosts, you can also configure a strategy for selecting the host to connect to.
        """.trimIndent()


        block {
            @Serializable
            data class TestDocument(
                val name: String,
                val tags: List<String>? = null
            )
        }

        +"""
            In the example below we will use this `TestDocument`, which we can serialize using the kotlinx.serialization 
            framework. You can also pass in your own serialized json in requests, so if you want to use e.g. jackson or gson,
            you can do so easily.
        """.trimIndent()

        block {
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
                // using the bulk DSL and a BulkSession
                // WaitFor ensures we can query for the documents
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
                        // but it has to be a single line in the bulk request
                        source =
                        """{"name":"banana","tags":["fruit","tropical"]}""",
                        index = indexName
                    )
                }

                // search for some fruit
                val results = client.search(indexName) {
                    query = bool {
                        must(
                            // note how we use property references here
                            term(TestDocument::tags, "fruit"),
                            matchPhrasePrefix(TestDocument::name, "app")
                        )
                    }
                }

                println("found ${results.total} hits")
                results
                    // extension function that deserializes
                    // uses kotlinx.serialization
                    .parseHits<TestDocument>()
                    .first()
                    // hits don't always include source
                    // in that case it will be a null document
                    .let {
                        println("doc ${it.name}")
                    }
                // you can also get the JsonObject if you don't
                // have a model class
                println(results.hits?.hits?.first()?.source)
            }
        }
        
        +"""
            This example shows off a few nice features of this library:
            
            - There is a convenient mapping and settings DSL (Domain Specific Language) that you can use to create indices.
            - In the mappings and in your queries, you can use kotlin property references instead of
            field names.
            - We have a bulk DSL. The `bulk` block
            creates a `BulkSession` for you and it deals with sending bulk requests and picking
            the responses apart for error handling. BulkSession has a lot of optional features that you can use: 
            it has item callbacks, you can specify the refresh parameter, you can make it 
            fail on the first item failure, etc. Alternatively, you can make it robust against
            failures, implement error handling and retries, etc.
            - You can use kotlinx.serialization for your documents but you don't have to. When using `kt-search` on the
            jvm you might want to use alternative json frameworks.
            
            For more details, refer to the [manual](https://jillesvangurp.github.io/kt-search/manual).
        """.trimIndent()
    }


    includeMdFile("readme-outro.md")
}
