package documentation.manual.indexrepo

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.Node
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.index
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.matchAll
import documentation.sourceGitRepository
import kotlinx.serialization.Serializable

@Suppress("NAME_SHADOWING")
val indexRepoMd = sourceGitRepository.md {
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))
    @Serializable
    data class TestDoc(val message: String)
    val repo = client.repository("test", TestDoc.serializer())

    +"""
        To cut down on the amount of copy pasting of aliases and index names, kt-search includes 
        a useful abstraction: the `IndexRepository`.
        
        An `IndexRepository` allows you to work with a specific index. You can perform document CRUD, query,
        do bulk indexing, etc. Additionally, you can configure read and write aliases and ensure the correct
        aliases are used.
        
    """.trimIndent()

    section("Creating a repository") {
        @Serializable
        data class TestDoc(val message: String)

        suspendingBlock(false) {
            val repo = client.repository("test", TestDoc.serializer())

            repo.createIndex {
                mappings {
                    text(TestDoc::message)
                }
            }
            val id = repo.index(TestDoc("A document")).id
            repo.delete(id)

            // and of course you can search in your index
            repo.search {
                query=matchAll()
            }
        }
    }

    section("Bulk Indexing") {
        suspendingBlock(false) {
            repo.bulk {
                // no need to specify the index
                index(TestDoc("test"))
                index(TestDoc("test1"))
                index(TestDoc("test2"))
                index(TestDoc("test3"))
            }
        }
    }

    section("Optimistic locking and updates") {
        +"""
            Elasticsearch is of course not a database and it does not have transactions.
            
            However, it can do optimistic locking using primary_term and seq_no attributes that it exposes in 
            index responses or get document responses. Doing this is of course a bit fiddly. To make safe updates
            easier, you can use the update function instead.
            
        """.trimIndent()
        suspendingBlock(false) {
            val id = repo.index(TestDoc("A document")).id
            repo.update(id, maxRetries = 2) {oldVersion ->
                oldVersion.copy(message = "An updated document")
            }
        }
        +"""
            Conflicts might happen when a document is updated concurrently or when some time has 
            passed in between when you fetched the document and when you update it. The above function 
            fetches the document, applies your update, and then stores it. In case of a version conflict,
            it re-fetches and retries this a configurable number of times before failing.            
        """.trimIndent()
    }
}