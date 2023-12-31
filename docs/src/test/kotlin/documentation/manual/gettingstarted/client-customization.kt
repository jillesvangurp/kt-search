package documentation.manual.gettingstarted

import com.jillesvangurp.ktsearch.*
import documentation.sourceGitRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds


val clientConfiguration = sourceGitRepository.md {
    // test server runs on 9999, so we need to override
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))

    +"""
        One of the parameters on `SearchClient` is the `restClient` parameter which has a default value
        that uses the built in `KtorRestClient`. KtorRestClient has several constructors with parameters 
        with default values that you can override. The `client` parameter specifies the ktor http client
        that is used and for this we provide a `defaultKtorHttpClient` function that creates an apprpriate
        client for jvm, js, or native platforms. But of course you can create your own and choose one of the 
        many ktor client engines that are available.       
    """.trimIndent()

    section("KtorRestClient client configuration") {

        +"""
            `KtorRestClient` allows you to control in detail how to
            connect to your cluster. The default parameters simply connect to localhost on port 9200 (the default
            http port for Elasticsearch and Opensearch. You can specify this explicitly as follows.
        """.trimIndent()
        example {
            val client3 = SearchClient(restClient = KtorRestClient("127.0.0.1", 9200))
        }

        +"""
            You may want to override some of other default parameter values to e.g. set up basic authentication and https. 
            For example, this is how you would
            connect to your cluster in Elastic Cloud using basic authentication.
        """.trimIndent()
        example {
            val client = SearchClient(
                KtorRestClient(
                    https = true,
                    user = "alice",
                    password = "secret",
                    nodes = arrayOf(Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
                )
            )
        }

        +"""
            If you have an API Key, you can set that up as follows:
        """.trimIndent()

        example {
            val client = SearchClient(
                KtorRestClient(
                    https = true,
                    elasticApiKey = "<SECRETKEY>",
                    nodes = arrayOf(Node("xxxxx.europe-west3.gcp.cloud.es.io", 9243))
                )
            )
        }

        +"""
            For Opensearch clusters in AWS, you need to use Amazon's [sigv4](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_aws-signing.html) to sign requests. This is currently 
            not directly supported directly in the client. However, it should be pretty easy to construct your own rest client that does this.
             
             See this [gist](https://gist.github.com/hassaku63/e3ed3cac288d429563cdddf1768613d6) on how
             to do curl requests against an AWS Opensearch cluster.
            
            To work around this, you can provide your own customized ktor client that 
             does this; or provide an alternative `RestClient` implementation in case you don't want to use ktor client. 
             
             Pull requests that document authenticating with opensearch in AWS in more detail 
             or enable this in a multi platform way with the ktor client are welcome. I currently
             do not have access to an opensearch cluster in AWS.
        """.trimIndent()

    }

    section("Node selectors") {

        +"""
            For unmanaged clusters or clusters without a load balancer,you can specify multiple nodes and use a node selection strategy to 
            pick one for each client call. Managed clusters such as 
            Elastic cloud or AWS Opensearch of course have a load balancer and it is generally not even
            possible to reach the individual nodes directly from the client.
                        
            There are currently two `NodeSelector` implementations. The `RoundRobinNodeSelector` and the 
            `SniffingNodeSelector`. If you need some other behavior, you can easily add your own node selection strategy 
            by implementing the `NodeSelector` interface.
        """.trimIndent()

        example {
            val nodes = arrayOf(
                Node("192.168.0.10", 9200),
                Node("192.168.0.11", 9200),
                Node("192.168.0.12", 9200)
            )
            val client = SearchClient(
                KtorRestClient(
                    nodes = nodes,
                    nodeSelector = RoundRobinNodeSelector(nodes),
                )
            )
        }
        +"""
            The round robin strategy picks a client based on the thread id (jvm only) or sequentially (other platforms).            
            using a simple round robin strategy. This strategy is appropriate if you don't plan to change the 
            cluster nodes regularly.              
            
        """.trimIndent()

        +"""
            The `SniffingNodeSelector` is a bit smarter and periodically retrieves the active list of 
             cluster nodes from the cluster. This strategy is useful if you need to perform cluster 
             modifications without downtime. E.g. adding new nodes to the cluster and removing existing nodes
             during an update would cause the node configuration you used to get out of date. The 
             `SniffingNodeSelector` updates it's list of nodes as this happens and you should not need
             to restart your application servers. At least not right away, you may still want to update
             it's initial list of nodes of course.
            
        """.trimIndent()

        suspendingExample(runExample = false) {
            val nodes = arrayOf(
                Node("localhost", 9200),
                Node("127.0.0.1", 9201)
            )
            val client = SearchClient(
                KtorRestClient(
                    nodes = nodes,

                    nodeSelector = SniffingNodeSelector(
                        initialNodes = nodes,
                        maxNodeAge = 3.seconds
                    ),
                )
            )
        }

        +"""
            To control which node is used with the `SniffingNodeSelector`, you can use an `AffinityId` co routine scope. 
            
            This ensures that repeated calls within the same co-routine scope use the same client. Without this, 
            it uses the thread name as the affinityId (jvm only) or randomly picks an active node (other platforms). 
            
            Using an affinity id helps performance a bit if you do multiple elastic search calls
            in one web request or transaction and benefits from http pipelining and connection reuse.
            
            Regardless of whether you use an affinity id, the node list is refreshed periodically (as per the `maxNodeAge` parameter).
        """.trimIndent()

        suspendingExample(runExample = false) {
            coroutineScope {
                async(AffinityId("myid")) {
                    // always ends up using the same node
                    client.root()
                }
                // without AffinityId:
                //  - on jvm: uses the thread name as the affinityId
                //  - on js: randomly picks a node
                // with both it periodically refreshes its list of nodes
                async {
                    client.root()
                }
            }
        }
    }

    section("RestClient & Error Handling") {
        +"""
            The `RestClient` interface defines functions to execute generic HTTP requests 
            against any REST endpoint. This is the equivalent of the low level rest client in the old client. The SearchClient is the equivalent of the high level client
            
            This library includes a default implementation for RestClient that uses ktor-client. You 
            can of course substitute your own implementation if you want to.
            
            However, the default KtorRestClient is based on the multiplatform ktor-client library. Which has appropriate implementations for its engine on
            each platform. So, you should be fine using that. If you create the `SearchClient` without a custom `RestClient`, it uses a sensible default client
            On the jvm, that uses the in Java engine instead of the CIO engine because of some
            concurrency related bugs that we encountered with that. There are several other client engines for the jvm that
             you might use.
              
            Most of the client functions in this library unwrap the `RestResponse` to either parse the
             response and return an appropriate model class. Or they throw a `RestException` with a status field otherwise. This makes it easy
             to determine success or failure. Either you get the appropriate response object
             or an exception.
             
            The wrapped `RestResponse` in the exception is a sealed class. So, if you need to, you can do an exhaustive 
            `when` on the type. You can also get the original response bytes if for whatever reason you wish to
            pick apart the error response.
             
            It is generally clear from the status code what the problem is and you can refer 
            to the exception message for details or turn on logging to debug the raw responses. The `KtorRestClient` 
            has a logging parameter for this.
            
            The sealed class includes sub classes for all the common exceptions that Elasticsearch is known to throw. In case
            of an something not covered by these, you will get a `UnexpectedStatus`. If you do, please file a bug or
            create a pull request to cover the un handled status code.
        """.trimIndent()
    }

    section("JSON handling") {
        +"""
            The `SearchClient` has a `json` parameter that specifies the kotlinx.serialization `Json` instance that 
            is used for parsing/serializing json.
            
            The default value for this is a carefully constructed instance that is configured
            to be lenient and does the right thing with e.g. nulls and kotlin default values. 
            
            But you can of course use your own instance should you need to. Note, the kotlinx.serialization defaults are pretty 
            terrible for the real world. So, be aware of this if you provide a custom instance.
                       
            There are two instances included with this library that are used by default that you may use here:
            
            - `DEFAULT_JSON` this is what is used by default
            - `DEFAULT_PRETTY_JSON` a pretty printing variant of DEFAULT_JSON that otherwise has the same settings.
        """.trimIndent()

        example {
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
                // adding new enum values is OK even if older clients won't understand it
                // they should be forward compatible
                ignoreUnknownKeys = true
                // decode missing enum values as null
                coerceInputValues = true
            }
        }
    }
}