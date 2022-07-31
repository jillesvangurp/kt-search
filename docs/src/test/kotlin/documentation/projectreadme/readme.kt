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

Kt-search is a pure Kotlin Multi Platform library to search across the Opensearch and Elasticsearch ecosystem. It includes rich Kotlin DSLs for querying, defining mappings, and more. It relies on all the latest and greatest multi-platform Kotlin features that you love: co-routines, kotlinx.serialization, ktor-client 2.x.

Because it is a multi-platform library you can embed it in your server (Ktor, Spring Boot, Quarkus), use it in a browser using kotlin-js, or embed it in your Android/IOS apps.

Learn how to integrate advanced search in your Kotlin applications. Whether you are building a web based dashboard, an advanced ETL pipeline, or simply exposing a search endpoint in as a microservice, this library has you covered.


It builds on other multi-platform libraries such as `ktor-client` and `kotlinx-serialization`. The goal for this library is to be the most convenient way to use opensearch and elasticsearch from Kotlin on any platform where Kotlin compiles.        
    """.trimIndent()

    includeMdFile("gradle.md")
    includeMdFile("readme-intro.md")

    +"""
     ## Usage
     """.trimIndent()

    block() {
        // we'll use a data class with kotlinx.serialization
        // you can use whatever json framework for your documents
        // of course
        @Serializable
        data class TestDocument(
            val name: String,
            val tags: List<String>? = null
        ) {
            fun json(pretty: Boolean = false): String {
                return if (pretty)
                    DEFAULT_PRETTY_JSON.encodeToString(serializer(), this)
                else
                    DEFAULT_JSON.encodeToString(serializer(), this)
            }
        }

        val client = SearchClient(
            // for now ktor client is the only supported client
            // but it's easy to provide alternate transports
            KtorRestClient(
                // our test server runs on port 9999
                nodes = arrayOf(
                    Node("localhost", 9999)
                )
            )
            // both SearchClient and KtorRestClient use sensible
            // but overridable defaults for lots of things
        )

        // we'll generate a random index name
        val indexName = "index-${Clock.System.now().toEpochMilliseconds()}"

        // most client functions are suspending, so lets use runBlocking
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
                    source = TestDocument(
                        name = "apple",
                        tags = listOf("fruit")
                    ).json(false),
                    index = indexName
                )
                index(
                    source = TestDocument(
                        name = "orange",
                        tags = listOf("fruit", "citrus")
                    ).json(false),
                    index = indexName,
                )
                index(
                    source = TestDocument(
                        name = "banana",
                        tags = listOf("fruit", "tropical")
                    ).json(false),
                    index = indexName
                )
            }
            // now let's search using the search DSL
            client.search(indexName) {
                query = bool {
                    must(
                        term(TestDocument::tags, "fruit"),
                        matchPhrasePrefix(TestDocument::name, "app")
                    )
                }
            }.let { results ->
                println("Hits: ${results.total}")
                println(results.hits?.hits?.first()?.source)
            }
        }
    }

    +"""
        For more details, check the tests. A full manual will follow soon.
    """.trimIndent()
    includeMdFile("readme-outro.md")
}
