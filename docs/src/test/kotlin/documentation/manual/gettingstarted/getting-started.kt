package documentation.manual.gettingstarted

import com.jillesvangurp.ktsearch.*
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking

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
        +"""
            You will probably want to override some of the default parameter values. For example, this is how you would
            connect to your cluster in Elastic Cloud.
        """.trimIndent()
        block {
            val client2=SearchClient(
                KtorRestClient(
                    https = true,
                    user = "alice",
                    password ="secret",
                    nodes = arrayOf( Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
                )
            )
        }
    }

}