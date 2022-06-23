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

val gettingStartedMd = sourceGitRepository.md {
    includeMdFile("gettingstarted.md")
    section("Creating a Client") {
        +"""
            First you have to create a client. Similar to what the Elastic and Opensearch Java client do, there is a
            simple `RestClient` interface that currently has a default implementation based on `ktor-client`. This client
            takes care of sending http calls to your search cluster.
        """.trimIndent()
        block {
            val client = SearchClient()
        }

        // test server runs on 9999, so we need to override
        val client = SearchClient(KtorRestClient(Node("localhost",9999)))
        block {
            runBlocking {
                // all apis are `suspend` functions, so you need a co-routine scope
                client.root().let { resp ->
                    println("${resp.variantInfo.variant}: ${resp.version.number}")
                }
                client.clusterHealth().let {resp ->
                    println(resp.clusterName + " is " + resp.status)
                }
            }
        }
        section("Alternative ways to create a client") {
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
        }
        section("JSON handling") {
            +"""
                The `SearchClient` has a json parameter with the kotlinx.serialization `Json` 
                that has a default value with a carefully constructed instance that is configured
                to be lenient and do the right thing with e.g. nulls and default values. But you 
                can of course use your own instance should you need to.
                           
                There are two instances included with this library that you may use here:
                
                - `DEFAULT_JSON` this is what is used by default
                - `DEFAULT_PRETTY_JSON` a pretty printing variant of DEFAULT_JSON that otherwise has the same settings.
            """.trimIndent()
        }

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
                ignoreUnknownKeys=true
            }
        }
    }
}