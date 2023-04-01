@file:Suppress("UNUSED_VARIABLE", "NAME_SHADOWING", "unused")

package documentation.projectreadme

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.githubLink
import documentation.manual.ManualPages
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
            First we create a client. Kotlin has default values for parameters. So, we use sensible defaults for the 
            `host` and `port` variables to connect to `localhohst` and `9200`. You can also configure multiple hosts, 
            or add ssl and basic authentication to connect to managed Opensearch or Elasticsearch clusters. If you use
            multiple hosts, you can also configure a strategy for selecting the host to connect to. For more on 
            this, read the [manual](${ManualPages.GettingStarted.publicLink}).
        """.trimIndent()


        block {
            @Serializable
            data class TestDocument(
                val name: String,
                val tags: List<String>? = null
            )
        }

        +"""
            In the example below we will use this `TestDocument`, which we can serialize using the 
            [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 
            framework. You can also pass in your own serialized json in requests, so if you want to use e.g. jackson or gson,
            you can do so easily.
        """.trimIndent()
        val indexName = "readme-index"
        runBlocking {  runCatching { client.deleteIndex(indexName) } }

        suspendingBlock(captureBlockReturnValue = false) {
            val indexName = "readme-index"

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
        }

        +"""
            To fill the index with some content, we need to use bulk operations.
            
            In kt-search this is made very easy with a DSL that abstracts away the book keeping
            that you need to do for this. The bulk block below creates a BulkSession, which flushes
            operations to Elasticsearch for you. You can configure and tailor how this works via parameters
            that have sensible defaults. For example the number of operations that is flushes is something
            that you'd want to probably configure.
            
            The refresh parameter uses WaitFor as a default. This means that after the block exits, the documents
            will have been indexed and are available for searching. 
        """.trimIndent()
        suspendingBlock() {
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
        }
        +"""
            You can read more about 
            [bulk operations](${ManualPages.BulkIndexing.publicLink}) in the manual.
            
            Now that we have some documents in an index, we can do some queries:
        """.trimIndent()
        suspendingBlock {
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

        +"""
            Aggregations are also supported via a DSL:
        """.trimIndent()
        suspendingBlock {
            val resp = client.search(indexName) {
                // we don't care about retrieving hits
                resultSize = 0
                agg("by-tag", TermsAgg(TestDocument::tags) {
                    aggSize = 50
                    minDocCount = 1
                })
            }
            // picking the results apart is just as easy.
            resp.aggregations
                .termsResult("by-tag")
                .parsedBuckets.forEach { bucket ->
                    println("${bucket.parsed.key}: ${bucket.parsed.docCount}")
                }
        }


        +"""
            These examples show off a few nice features of this library:
            
            - Kotlin DSLs are nice, type safe, and easier to read and write than pure Json. And of course
            you get auto completion too. There are DSLs for searching, creating indices and mappings, datastreams, 
            index life cycle management, bulk operations, aggregations, and more. 
            - Where in JSON, you use a lot of String literals, kt-search actually allows you to use
             property references or enum values. So refactoring your data model doesn't 
             break your mappings and queries.
            - Kt-search makes complicated features like bulk operations, aggregations, etc. really easy 
            to use and accessible.
            - While a DSL is nice to have, sometimes it just doesn't have the feature you 
            need or maybe you want to work with raw json. Kt-search allows you to do both and mix 
            schema less with type safe kotlin. You can add custom 
            properties to the DSL via `put` or you can use Kotlin string literals to pass in and template
            raw json.
            - Kt-search is designed to be [extensible]${ManualPages.ExtendingTheDSL.publicLink})). 
            It's easy to use the built in features. But you 
            can easily add your own features. 
            
            There are of course a lot more features that this library supports. The 
            [manual](https://jillesvangurp.github.io/kt-search/manual) covers all of those.
        """.trimIndent()
    }

    includeMdFile("related.md")
    includeMdFile("readme-outro.md")
}
