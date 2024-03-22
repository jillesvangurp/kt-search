@file:Suppress("UNUSED_VARIABLE", "NAME_SHADOWING", "unused")

package documentation.projectreadme

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.*
import documentation.githubLink
import documentation.manual.ManualPages
import documentation.manual.sections
import documentation.printStdOut
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

val projectReadme = sourceGitRepository.md {
    +"""
[![matrix-test-and-deploy-docs]($githubLink/actions/workflows/deploy-docs-and-test.yml/badge.svg?branch=master)]($githubLink/actions/workflows/deploy-docs-and-test.yml)
    """.trimIndent()
    includeMdFile("oneliner.md")

    includeMdFile("readme-intro.md")

    includeMdFile("gradle.md")

    includeMdFile("maven.md")

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

        +"""
            ### Create a client
            
            First we create a client. 
        """.trimIndent()

        // this is what we show in the Readme :-)
        example {
            val client = SearchClient()
        }

        +"""
            Kotlin has default values for parameters. So, we use sensible defaults for the 
            `host` and `port` variables to connect to `localhost` and `9200`.
        """.trimIndent()
        example {
            val client = SearchClient(
                KtorRestClient(host="localhost", port=9200)
            )
        }

        +"""
            If you need ro, you can also configure multiple hosts, 
            add ssl and basic authentication to connect to managed Opensearch or Elasticsearch clusters. If you use
            multiple hosts, you can also configure a strategy for selecting the host to connect to. For more on 
            this, read the [manual](${ManualPages.GettingStarted.publicLink}).
        """.trimIndent()

        +"""
            ### Documents and data classes
            
            In Kotlin, the preferred way to deal with data would be a data class. This is a simple data class
            that we will use below.
        """.trimIndent()

        example {
            @Serializable
            data class TestDocument(
                val name: String,
                val tags: List<String>? = null
            )
        }

        +"""
            In the example below we will use this `TestDocument`, which we can serialize using the 
            [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) 
            framework. You can also pass in your own serialized json in requests, 
            so if you want to use e.g. jackson or gson instead,
            you can do so easily.
            
            ### Creating  an index           
            
        """.trimIndent()
        val indexName = "readme-index"
        runBlocking {  runCatching { client.deleteIndex(target = indexName, ignoreUnavailable = true) } }

        example {
            val indexName = "readme-index"

            // create an index and use our mappings dsl
            client.createIndex(indexName) {
                settings {
                    replicas = 0
                    shards = 3
                    refreshInterval = 10.seconds
                }
                mappings(dynamicEnabled = false) {
                    text(TestDocument::name)
                    keyword(TestDocument::tags)
                }
            }
        }

        +"""
            This creates the index and uses the mappings and settings DSL. With this DSL, you can map fields, 
            configure analyzers, etc. This is optional of course; you can just call it without the block 
            and use the defaults and rely on dynamic mapping. 
            You can read more about that [here](${ManualPages.IndexManagement.publicLink}) 
            
            ### Adding documents
            
            To fill the index with some content, we need to use bulk operations.
            
            In kt-search this is made very easy with a DSL that abstracts away the book keeping
            that you need to do for this. The bulk block below creates a `BulkSession`, which does this for you and flushes
            operations to Elasticsearch. You can configure and tailor how this works via parameters
            that have sensible defaults. For example the number of operations that is flushed is something
            that you'd want to probably configure.
            
            The optional `refresh` parameter uses WaitFor as the default. This means that after the block exits, the documents
            will have been indexed and are available for searching. 
        """.trimIndent()
        example {
            client.bulk(
                refresh = Refresh.WaitFor,
                // send operations every 2 ops
                // default would be 100
                bulkSize = 2,
            ) {
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
            
            ### Search
            
            Now that we have some documents in an index, we can do some queries:
        """.trimIndent()
        example {
            // search for some fruit
            val results = client.search(indexName) {
                query = bool {
                    must(
                        // note how we can use property references here
                        term(TestDocument::tags, "fruit"),
                        matchPhrasePrefix(TestDocument::name, "app")
                    )
                }
            }

            println("found ${results.total} hits")
            results
                // extension function that deserializes
                // the hits using kotlinx.serialization
                .parseHits<TestDocument>()
                .first()
                .let {
                    // we feel lucky
                    println("doc ${it.name}")
                }
            // you can also get the JsonObject if you don't
            // have a model class
            println(results.hits?.hits?.first()?.source)
        }.printStdOut()

        +"""
            You can also construct complex aggregations with the query DSL:
        """.trimIndent()
        example {
            val resp = client.search(indexName) {
                // we don't care about retrieving hits
                resultSize = 0
                agg("by-tag", TermsAgg(TestDocument::tags) {
                    // simple terms agg on the tags field
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
        }.printStdOut()


        +"""
            These examples show off a few nice features of this library:
            
            - Kotlin DSLs are nice, type safe, and easier to read and write than pure Json. And of course
            you get auto completion too. The client includes more DSLs for searching, creating indices and mappings, datastreams, 
            index life cycle management, bulk operations, aggregations, and more. 
            - Where in JSON, you use a lot of String literals, kt-search actually allows you to use
             property references or enum values as well. So, refactoring your data model doesn't 
             break your mappings and queries.
            - Kt-search makes complicated features like bulk operations, aggregations, etc. really easy 
            to use and accessible. And there is also the IndexRepository, which makes it extremely easy
            to work with and query documents in a given index or data stream.
            - While a DSL is nice to have, sometimes it just doesn't have the feature you 
            need or maybe you want to work with raw json string literal. Kt-search allows you to do both
            and mix schema less with type safe kotlin. You can easily add custom 
            properties to the DSL via a simple `put`. All `JsonDsl` are actually mutable maps.  
            - Kt-search is designed to be [extensible](${ManualPages.ExtendingTheDSL.publicLink}). 
            It's easy to use the built in features. And you can easily add your own features. This also
            works for plugins or new features that Elasticsearch or Opensearch add.
            
        """.trimIndent()
    }
    section("Manual") {
        +"""
            There are of course a lot more features that this library supports. The 
            [manual](https://jillesvangurp.github.io/kt-search/manual) covers all of those.
        """.trimIndent()
        sections.forEach {
            +"""
            ### ${it.title}
                
            """.trimIndent()
            it.pages.forEach {(mp,_) ->
                +"- [${mp.page.title}](${mp.publicLink})\n"
            }
        }
    }

    includeMdFile("related.md")
    includeMdFile("readme-outro.md")
}

