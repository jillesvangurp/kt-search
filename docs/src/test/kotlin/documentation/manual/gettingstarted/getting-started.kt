@file:Suppress("UNUSED_VARIABLE", "LocalVariableName")
@file:OptIn(ExperimentalSerializationApi::class)

package documentation.manual.gettingstarted

import com.jillesvangurp.ktsearch.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

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
    section("Create a Client") {
        +"""
            To use `kt-search` you need a `SearchClient` instance. Similar to what the Elastic and Opensearch Java client do, there is a
            simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
            takes care of sending HTTP calls to your search cluster.
        """.trimIndent()
        block {
            // creates a client with the default RestClient
            val client = SearchClient()
        }

        // test server runs on 9999, so we need to override
        val client = SearchClient(KtorRestClient(Node("localhost", 9999)))

        +"""
            After creating the client, you can use it. Since kt-search uses non blocking IO via ktor client, all 
            calls are suspending and have to be inside a co-routine.
        """.trimIndent()

        +"""
            You may want to override some of the default parameter values. For example, this is how you would
            connect to your cluster in Elastic Cloud.
        """.trimIndent()
        block {
            val client2 = SearchClient(
                KtorRestClient(
                    https = true,
                    user = "alice",
                    password = "secret",
                    nodes = arrayOf(Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
                )
            )
        }
        +"""
            `KtorRestClient` has an alternate constructor that you can use if
             you have a proxy in front of your cluster or only one node.
        """.trimIndent()
        block {
            val client3 = SearchClient(KtorRestClient("127.0.0.1", 9200))
        }

        +"""
            You can also use multiple nodes and use a node selection strategy.
        """.trimIndent()

        block {
            val nodes= arrayOf(
                Node("127.0.0.1", 9200),
                Node("127.0.0.1", 9201),
                Node("127.0.0.1", 9202)
            )
            val client4 = SearchClient(
                KtorRestClient(
                    nodes = nodes,
                    nodeSelector = RoundRobinNodeSelector(nodes),
                )
            )
        }
        +"""
            There are currently just one NodeSelector implementation that implements a simple round robin
            strategy. Note, it currently does not attempt to detect failing nodes or implements any cluster 
            sniffing. This is something that may be added later (pull requests welcome). 
            
            You can easily add your own node selection strategy by implementing the `NodeSelector` interface.
        """.trimIndent()

        block {
            runBlocking {
                client.root().let { resp ->
                    println("${resp.variantInfo.variant}: ${resp.version.number}")
                }
                client.clusterHealth().let { resp ->
                    println(resp.clusterName + " is " + resp.status)
                }
            }
        }
    }

    section("JSON handling") {
        +"""
            The `SearchClient` has a json parameter with the kotlinx.serialization `Json` 
            that has a default value with a carefully constructed instance that is configured
            to be lenient and do the right thing with e.g. nulls and default values. But you 
            can of course use your own instance should you need to.
                       
            There are two instances included with this library that are used by default that you may use here:
            
            - `DEFAULT_JSON` this is what is used by default
            - `DEFAULT_PRETTY_JSON` a pretty printing variant of DEFAULT_JSON that otherwise has the same settings.
        """.trimIndent()

        block {
            val DEFAULT_JSON = Json {
                // don't rely on external systems being written in kotlin
                // or even having a language with default values
                // the default of false is insane and dangerous
                encodeDefaults = true
                // save space
                prettyPrint = false
                // people adding things to the json is OK, we're forward compatible
                // and will just ignore it
                isLenient = true
                // encoding nulls is meaningless and a waste of space.
                explicitNulls = false
                // adding enum values is OK even if older clients won't understand it
                ignoreUnknownKeys = true
                // will decode missing enum values as null
                coerceInputValues = true
            }
        }
    }
}