@file:Suppress("UNUSED_VARIABLE", "LocalVariableName")

package documentation.manual.gettingstarted

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.matchAll
import com.jillesvangurp.searchdsls.querydsl.matchPhrase
import com.jillesvangurp.searchdsls.querydsl.term
import documentation.manual.ManualPages
import documentation.mdLink
import documentation.sourceGitRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

val whatIsKtSearchMd = sourceGitRepository.md {
    includeMdFile("whatisktsearch.md")
}

@OptIn(ExperimentalSerializationApi::class)
val gettingStartedMd = sourceGitRepository.md {
    +"""
        To get started, simply add the dependency to your project and create a client. 
        The process is the same for both jvm and kotlin-js.
    """.trimIndent()
    includeMdFile("../../projectreadme/gradle.md")
    // test server runs on 9999, so we need to override
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))

    section("Create a Client") {
        +"""
            To use `kt-search` you need a `SearchClient` instance. Similar to what the Elastic and Opensearch Java client do, there is a
            simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
            takes care of sending HTTP calls to your search cluster.
        """.trimIndent()
        example {
            // creates a client with the default RestClient
            val client = SearchClient()
        }
    }

    section("Using the client") {
        +"""
            After creating the client, you can use it. Since kt-search uses non blocking IO via ktor client, all 
            calls are suspending and have to be inside a co-routine.
        """.trimIndent()

        example {
            // use a simple runBlocking
            // normally you would get a co-routine via e.g. Spring's flux async framework.
            runBlocking {
                // call the root API with some version information
                client.root().let { resp ->
                    println("${resp.variantInfo.variant}: ${resp.version.number}")
                }
                // get the cluster health
                client.clusterHealth().let { resp ->
                    println(resp.clusterName + " is " + resp.status)
                }

            }
        }

        +"""
            The main purpose of kt-search is of course searching. This is how you do a simple search and work with 
            data classes:
        """.trimIndent()
        suspendingExample(runExample = false) {

            // define a model for your indexed json documents
            data class MyModelClass(val title: String, )

            // a simple search
            val results = client.search("myindex") {
                query = matchPhrase(
                    field = "title",
                    query = "lorum ipsum")
            }

            // returns a list of MyModelClass
            val parsedHits = results.parseHits<MyModelClass>()

            // if you don't have a model class, you can just use a JsonObject
            val jsonObjects = results
                .hits
                ?.hits
                // extract the source from the hits (JsonObject)
                ?.map { it.source }
                // fall back to empty list
                ?: listOf()
        }
    }

    section("Next steps") {
        +"""
            - ${ManualPages.ClientConfiguration.page.mdLink}: Learn how to customize the client further.
            - ${ManualPages.Search.page.mdLink}: Learn more about how to use the query DSL.
            - ${ManualPages.IndexRepository.page.mdLink}: Configure an index repository to make working with a specific index easier.
        """.trimIndent()
    }
}