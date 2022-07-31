package documentation.manual.crud

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.indexDocument
import documentation.sourceGitRepository
import kotlinx.serialization.Serializable

val crudMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))
    @Serializable
    data class TestDoc(val id: String, val name: String, val tags: List<String> = listOf())

    +"""
        Mostly, you will use bulk indexing to manipulate documents in Elasticsearch. However, 
        sometimes it is useful to be able to manipulate individual documents with the 
        Create, Read, Update, and Delete (CRUD) APIs.
    """.trimIndent()

    suspendingBlock {
        client.indexDocument(
            target = "myindex",
            document = TestDoc("1", "A Document"),
            id = "1"
        )

    }
}