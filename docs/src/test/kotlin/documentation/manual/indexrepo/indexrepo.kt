package documentation.manual.indexrepo

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.match
import com.jillesvangurp.searchdsls.querydsl.matchAll
import documentation.manual.ManualPages
import documentation.manual.bulk.bulkMd
import documentation.mdLink
import documentation.sourceGitRepository
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

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

    section("Deserializing documents") {
        +"""
            The main purpose of the `IndexRepository` is to handle document deserialization for you. To enable this
            a few extension functions are provided that allow you to deserialize individual hits or lists and flows of 
            hits. 
        """.trimIndent()

        suspendingBlock(runBlock = false) {
            val documents: List<TestDoc> = repo.search {
                query = match(TestDoc::message, "document")
            }.parseHits<TestDoc>()
        }

        +"""
            The disadvantage of extension functions is that they are not aware of the `ModelSerializationStrategy`.
            The version above is uses reified inline generics. There are also variants that take either the 
            `ModelSerializationStrategy` or the kotlinx serialization strategy.
            
            But there's also a short hand in the `IndexRepository` which doesn't have this disadvantage.
            
        """.trimIndent()
        suspendingBlock(runBlock = false) {
            val documents: List<TestDoc> = repo.searchDocuments {
                query = match(TestDoc::message, "document")
            }
        }
        +"""
            This also works for `search_after`, which returns a flow of hits that you can turn
            into a flow of your document
            
            Likewise you can get a document with `getDocument`:
        """.trimIndent()
        suspendingBlock(runBlock = false) {
            // returns null if the document is not found
            val doc: TestDoc? = repo.getDocument("42")
        }

        +"""
            Or get both the document and the `GetResponse` by destructuring:
        """.trimIndent()
        suspendingBlock(runBlock = false) {
            // throws a RestException if the document is not found
            val (doc: TestDoc,resp: GetDocumentResponse) = repo.get("42")
        }

        +"""
            Note. the type specifications above are of course optional because of type inference but added for readability.
        """.trimIndent()
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

    section("Multi Get") {
        +"""
            Multi get is of course also supported.
        """.trimIndent()
        
        suspendingBlock(false) {
            repo.bulk {
                index(TestDoc("One"), id = "1")
                index(TestDoc("Two"), id = "2")
            }
            // multi get can be very convenient
            repo.mGet("1","2")
            // but you can also do use the full dsl
            repo.mGet {
                ids = listOf("1","2")
            }
            // or if you insist
            repo.mGet {
                doc {
                    id="1"
                    source=false
                }
                doc {
                    id="2"
                }
            }
        }
    }

    section("Optimistic locking and updates") {
        +"""
            Elasticsearch is of course not intended to be a database and it does not have transactions. However,
            it does have a few features that allow you to (ab)use it as one.
            
            Elasticsearch supports optimistic locking. With optimistic locking you can guarantee that you are not 
            overwriting concurrent updates to documents. Additionally, most write operations have a refresh parameter that you can set to `wait_for`
            to ensure read consistency (read your own writes). Both features combined, make it possible to use 
            Elasticsearch as a simple document store.
            
            Optimistic locking works by setting the `if_primary_term` and `if_seq_no` 
            parameters on indexing operations and handling the version conflict http response by trying again with
            a freshly fetched version of the document that has the current values of `primary_term` and `seq_no`. 
            
            Conflicts happen any time you have concurrent writes updating a document in between when you fetch it 
            and when you attempt to replace it. By specifying `if_primary_term` and `if_seq_no`, the conflict is
            detected and you get a version conflict response.
            
            It is called optimistic locking because instead of locking, it simply applies a cheap check that 
            can fail that you can then act on by retrying. Since nothing gets locked, everything stays fast. 
            And with a rare retry operation, performance should not suffer.
            
            Dealing with this is of course a bit fiddly to do manually. To make optimistic locking really easy,
            the `IndexRepository` supports updates with retry both for single documents and with bulk operations.  
            
        """.trimIndent()
        suspendingBlock(false) {
            val id = repo.index(TestDoc("A document")).id
            repo.update(id, maxRetries = 2) { oldVersion ->
                oldVersion.copy(message = "An updated document")
            }
        }
        +"""
            This fetches the document and its `primary_term` and `seq_no` values, applies your update function, 
            and then stores it. In case of a version conflict, it re-fetches the document with the latest 
            `primary_term` and `seq_no` values, and then re-applies your update
            function to that version. The number of retries is configurable. If all retries fail, you will get a 
            version conflict exception. The only time this may happen is if you have a lot of concurrent writes 
            to the same documents.
        """.trimIndent()

        section("Bulk updates and optimistic locking") {

            +"""
                You may also want to apply optimistic locking to bulk updates and it has a similar mechanism for
                setting `if_primary_term` and `if_seq_no`. The index repository implements an extended version of the
                BulkSession that includes update functions similar to the above and uses a callback based retry mechanism.
                
                See ${ManualPages.BulkIndexing.page.mdLink} for more information on callbacks.                                
            """.trimIndent()
            suspendingBlock(false) {
                val aDoc = TestDoc("A document")
                val id = repo.index(aDoc).id
                repo.bulk(
                    // these parameters are optional
                    // and have sensible defaults
                    maxRetries = 1,
                    retryTimeout = 2.seconds
                ) {
                    update(
                        id = id,
                        // you have to provide the original
                        original = aDoc,
                        // and the seq_no and primary_term
                        // these values are probably wrong
                        // amd will trigger a retry
                        ifSeqNo = 42,
                        ifPrimaryTerm = 42
                    ) {
                        // like before, we use a function block
                        // to make the changes
                        it.copy(message = "Changed")
                    }
                }
            }

            +"""
                Digging out primary_term and seq_no numbers manually is of course a bit tedious. 
                As an alternative, you pass in anything that implements `SourceInformation`. This includes document
                get responses, multi get responses, and search hits.
            """.trimIndent()

            suspendingBlock(false) {
                val aDoc = TestDoc("A document")
                val id = repo.index(aDoc).id

                val (_, getResponse) = repo.get(id)
                // note, you should use a multi get if you are updating many documents
                repo.index(
                    value = aDoc.copy("This will be overwritten"),
                    id = getResponse.id
                )
                repo.bulk {
                    update(
                        // GetResponse implements SourceInformation
                        // however, our getResponse is now out of date
                        // so it will retry
                        getResponse
                    ) {
                        it.copy(message = "Changed it again")
                    }
                }

            }

            +"""
                Using searchAfter is great for
                applying large amounts of updates to an index. This is how that works:
            """.trimIndent()

            suspendingBlock(false) {
                repo.bulk {
                    repo.searchAfter {
                        // this is needed because we need _seq_no and _primary_term
                        seqNoPrimaryTerm = true
                        query = matchAll()
                    }.let { (firstResponse, hitFlow) ->
                        // this will page through the entire index
                        hitFlow.collect { hit ->
                            // if somebody messes with the index while we do this
                            // bulk update will just retry it
                            update(
                                // hit implements SourceInformation
                                hit
                            ) {
                                it.copy(message = it.message.reversed())
                            }
                        }
                    }
                }
            }
        }
    }
}