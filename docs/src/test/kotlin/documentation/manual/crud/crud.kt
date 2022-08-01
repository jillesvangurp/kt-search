package documentation.manual.crud

import com.jillesvangurp.ktsearch.*
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

    suspendingBlock(false) {
        // create
        val resp = client.indexDocument(
            target = "myindex",
            document = TestDoc("1", "A Document"),
            // optional id, you can let elasticsearch assign one
            id = "1",
            // this is the default
            // fails if the id already exists
            opType = OperationType.Create
        )

        // read
        val doc = client.getDocument("myindex", resp.id)
            // source is a JsonDoc, which you can deserialize
            // with an extension function
            .source.parse<TestDoc>()

        // update
        client.indexDocument(
            target = "myindex",
            document = TestDoc("1", "A Document"),
            id = "1",
            // will overwrite if the id already existed
            opType = OperationType.Index
        )

        // delete
        client.deleteDocument("myindex", resp.id)
    }

    +"""
        The index API has a lot more parameters that are supported here as well
        via nullable parameters. You can also use a variant of the index API
        that accepts a json String instead of the TestDoc.
    """.trimIndent()
}