package documentation.manual.bulk.indexmanagement

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import documentation.manual.ManualPages
import documentation.mdLink
import documentation.sourceGitRepository
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.hours

val dataStreamsMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))
    cleanup(client)
    +"""
        Particularly for large volume time series data, you can use data streams to make the management of 
        indices a bit easier. Data streams allow you to automate a lot of the things you would otherwise do manually 
        with manually ${ManualPages.IndexManagement.page.mdLink}.  
                
        A data stream is simply a set of indices that are managed and controlled by Elasticsearch. 
        To create a data stream, you need to define policies and templates that tell Elasticsearch how to do this.
        
        - Index Life-cycle managent is used to control how the index is managed and ultimately deleted over time.
        - Index templates and index component templates are used to control the mappings and settings for the indices.
        - Finally you can use the data stream API to control and introspect the data stream.
        
    """.trimIndent()


    section("Index Life Cycle Management") {
        +"""
            It is advisable to set up index life cycle management (ILM) with data streams. 
            Using ILM, you can automatically roll over indices, shrink them and delete them.
            
            Index life cycle management is an Elastic only feature. However, Opensearch has a similar 
            feature, called Index State Management. At this point we do not support this. But pull 
            requests are welcome for this of course.
            
            For a full overview of ILM see the Elastic documentation for this.
        """.trimMargin()

        example {
            client.setIlmPolicy("my-ilm") {
                hot {
                    // this is where your data goes
                    actions {
                        rollOver(maxPrimaryShardSizeGb = 2)
                    }
                }
                warm {
                    // indices get rolled over to this
                    // and are still queryable
                    // of course we use Duration here
                    minAge(24.hours)
                    actions {
                        shrink(numberOfShards = 1)
                        forceMerge(numberOfSegments = 1)
                    }
                }
            }
        }
    }


    section("Index Templates") {
        +"""
            Once you have defined an ILM policy, you can refer it in an index template. An index template
            consists of index component templates. So we have to define those first.
        """.trimIndent()
        example {

            // using component templates is a good idea
            // note, Elastic bundles quite a few default ones that you can use
            client.updateComponentTemplate("my-logs-settings") {
                settings {
                    replicas = 4
                    indexLifeCycleName = "my-ilm"
                }
            }
            client.updateComponentTemplate("my-logs-mappings") {
                mappings {
                    text("name")
                    keyword("category")
                    // note data streams require @timestamp
                    date("@timestamp")
                }
            }
            // now create the template
            client.createIndexTemplate("my-logs-template") {
                indexPatterns = listOf("my-logs*")
                // make sure to specify an empty object for data_stream
                dataStream = withJsonDsl {
                    // the elastic docs are a bit vague on what goes here
                }
                composedOf = listOf("my-logs-settings", "my-logs-mappings")

                // in case multiple templates can be applied, the ones
                // with the highest priority wins. The managed ones
                // that come with Elastic have a priority of 100
                priority = 200
            }

            client.createDataStream("my-logs")
        }
        cleanup(client)
    }
}

private fun cleanup(client: SearchClient) {
    runBlocking {
        runCatching { client.deleteDataStream("my-logs") }
        runCatching { client.deleteIndexTemplate("my-logs-template") }
        runCatching { client.deleteComponentTemplate("my-logs-settings") }
        runCatching { client.deleteComponentTemplate("my-logs-mappings") }
        runCatching { client.deleteIlmPolicy("my-ilm") }
    }
}