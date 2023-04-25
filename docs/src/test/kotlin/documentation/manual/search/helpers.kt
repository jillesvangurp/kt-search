package documentation.manual.search

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class TestDoc(val id: String, val name: String, val tags: List<String> = listOf(), val price: Double)

val client by lazy { SearchClient(KtorRestClient("localhost", 9999, logging = true)) }

fun SearchClient.indexTestFixture(indexName: String) = runBlocking {
    // begin INITTESTFIXTURE
    // re-create the index
    deleteIndex(indexName)
    createIndex(indexName) {
        mappings {
            text(TestDoc::name)
            keyword(TestDoc::tags) {
                fields {
                    text("txt")
                }
            }
            number<Double>(TestDoc::price)
        }
    }

    val docs = listOf(
        TestDoc(
            id = "1",
            name = "Apple",
            tags = listOf("fruit"),
            price = 0.50
        ),
        TestDoc(
            id = "2",
            name = "Banana",
            tags = listOf("fruit"),
            price = 0.80
        ),
        TestDoc(
            id = "3",
            name = "Green Beans",
            tags = listOf("legumes"),
            price = 1.20
        )
    )
    docs.forEach { d ->
        client.indexDocument(
            target = indexName,
            document = d,
            id = d.id,
            refresh = Refresh.WaitFor
        )
    }
    // end INITTESTFIXTURE

}


// begin RESULTSPRETTYPRINT
fun SearchResponse.pretty(message: String): String =
    // simple extension function to print the results
    "$message Found ${total} results:\n" +
            hits!!.hits.joinToString("\n") { h ->
                "- ${h.score} ${h.id} ${h.parseHit<TestDoc>().name}"
            }
// end RESULTSPRETTYPRINT

