package documentation.manual.crud

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.Script
import documentation.manual.ManualPages
import documentation.mdLink
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
        Elasticsearch also has a dedicated update API that you can use with either a partial document or a script.
    """.trimIndent()

    suspendingBlock {
        client.indexDocument(
            target = "myindex",
            document = TestDoc("42", "x"),
            id = "42"
        )
        var resp = client.updateDocument(
            target = "myindex",
            id = "42",
            docJson = """{"name":"changed"}""",
            source = "true"
        )
        println(resp.get?.source)

        resp = client.updateDocument(
            target = "myindex",
            id = "42",
            script = Script.create {
                source = """ctx._source.name = params.p1 """
                params = mapOf(
                    "p1" to "again"
                )
            },
            source = "true"
        )
        println(resp.get?.source)

    }

    +"""
        The index API has a lot more parameters that are supported here as well
        via nullable parameters. You can also use a variant of the index API
        that accepts a json String instead of the TestDoc.
        
        Note, for inserting large amounts of documents you should of course use the bulk API. You can learn more about that here: ${
        ManualPages.BulkIndexing.page.mdLink}.
    """.trimIndent()
}