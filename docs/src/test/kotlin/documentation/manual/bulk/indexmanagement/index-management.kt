package documentation.manual.bulk.indexmanagement

import com.jillesvangurp.ktsearch.*
import documentation.manual.ManualPages
import documentation.mdLink
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking

val indexManagementMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))
    kotlin.runCatching {
        runBlocking {
            client.deleteIndex("an-index")
        }
    }

    section("Creating Indices") {
        +"""
            A Crucial part of working with Elasticsearch is creating and managing indices, mappings, and settings.
                    
            Getting started is easy, simply create an index like this:
        """.trimIndent()

        suspendingBlock(runBlock = false) {

            // creates an index with dynamic mapping turned on
            client.createIndex("my-first-index")

            // insert some document
            val json = """{"message:"hello world"}"""
            client.indexDocument("my-first-index", json)

            // and now it's gone
            client.deleteIndex("my-first-index")
        }
    }

    section("Using the mappings and settings DSL") {
        +"""
            While dynamic mapping is convenient when you are getting started, you should explictly manage your
            index mappings. For this, kt-search provides a convenient Mapping and Settings DSL
        """.trimIndent()

        suspendingBlock {
            data class TestDocument(
                val message: String,
                val number: Double,
                val properties: Map<String, String>,
                val tags: List<String>
            )


            client.createIndex("an-index") {
                settings {
                    replicas=1
                    shards=3

                    analysis {
                        filter("2_5_edgengrams") {
                            type = "edge_ngram"
                            // we don't directly support all params
                            // of each filter, tokenizer, etc.
                            // so use put to add anything that is missing
                            put("min_gram", 2)
                            put("max_gram", 5)
                        }
                        analyzer("prefix_ngrams") {
                            tokenizer = "whitespace"
                            filter = listOf("2_5_edgengrams")
                        }
                    }
                }
                mappings(dynamicEnabled = false) {
                    text(TestDocument::message) {
                        copyTo = listOf("catchall")
                        fields {
                            keyword("keyword")
                            text("completion") {
                                analyzer="prefix_ngrams"
                            }
                        }
                    }
                    text("catchall") {
                        norms = false
                    }
                    number<Double>(TestDocument::number)
                    objField(TestDocument::properties) {
                        keyword("foo")
                    }
                    keyword(TestDocument::tags) {
                        copyTo = listOf("catchall")
                    }
                }
            }
        }
        runBlocking {
            client.deleteIndex("an-index")
        }
        +"""
            This is a deliberately more elaborate example that shows off a few of the features of the DSL:
            
            - we customized the replicas and shards settings
            - we added a custom analyzer
            - we defined some field mappings and disabled dynamic mapping
            - the mappings include a few sub fields, a catchall field that we copy values to and we used our custom analyzer on  completion field
            
            As with all our DSLs, the objects are backed by `JsonDsl` which means you can freely add things using `put` that are  not explicitly supported by the DSL. There are a lot of included filters, tokenizers, etc. And more are available via plugins. So, full coverage of all their settings is not likely to happen.
        """.trimIndent()
    }

    section("Aliases") {
        +"""
            Aliases are a useful tool to manage indices over time. As your data mode evolves, you find yourself needing
            to create new indices with new mappings. Aliases allow this to happen independently of your query logic 
            and write logic.
            
            There are many ways to make use of aliases. A common pattern is to use e.g. `read-`, and `write-` aliases that 
            point to the actual index. By separating reads and writes, you can implement a reindex strategy 
            where writes  go to a new index and after you have reindexed the old index, you switch over the read alias
            so all your queries use the new index. After that, you can safely remove the old index.
        """.trimIndent()

        suspendingBlock(false) {
            client.createIndex("foo-1")

            client.updateAliases {
                add {
                    alias = "foo"
                    index = "foo-1"
                }
            }
            println(
                "Aliases for foo-1: ${
                    client.getAliases()["foo-1"]?.aliases?.keys
                }"
            )
            println(
                "Aliases for foo-2: ${
                    client.getAliases()["foo-2"]?.aliases?.keys
                }"
            )
            client.createIndex("foo-2")
            client.updateAliases {
                remove {
                    aliases = listOf("foo", "bar")
                    index = "foo-1"

                }
                add {
                    alias = "foo"
                    index = "foo-2"
                }
            }
            println(
                "Aliases for foo-1: ${
                    client.getAliases()["foo-1"]?.aliases?.keys
                }"
            )
            println(
                "Aliases for foo-2: ${
                    client.getAliases()["foo-2"]?.aliases?.keys
                }"
            )
            client.updateAliases {
                removeIndex {
                    // removes indices and any remaining aliases
                    indices = listOf("foo-1", "foo-2")
                }
            }
        }

        +"""
            Note. you may also want to consider using data streams instead: ${ManualPages.DataStreams.page.mdLink}
             
            Datastreams work in both Opensearch and Elasticsearch and automate some of the things around index management for timeseries data.
        """.trimIndent()
    }
}