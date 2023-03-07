@file:Suppress("unused", "UNUSED_VARIABLE")

package documentation.manual.bulk

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.ktsearch.repository.repository
import com.jillesvangurp.searchdsls.querydsl.Script
import documentation.sourceGitRepository
import kotlinx.serialization.Serializable


val bulkMd = sourceGitRepository.md {
    +"""
            An important part of working with Elasticsearch is adding content. While the CRUD support is useful
            for manipulating individual objects in an index, it is not suitable for sending large amounts of data.
            
            For that, bulk indexing should be used. The bulk API in Elasticsearch is one of the more complex APIs
            in ES. Kt-search provides a few key abstractions to make bulk indexing easy, robust, 
            and straightforward.
        """
    val client = SearchClient(KtorRestClient(Node("localhost", 9999)))

    @Serializable
    data class Foo(val foo: String)

    section("Bulk Sessions") {


        suspendingBlock(runBlock = false) {
            @Serializable
            data class Foo(val foo: String)

            client.createIndex("test") {
                mappings {
                    text(Foo::foo)
                }
            }
            // create a bulk session
            client.bulk {
                // inside the block we can call index, create, or delete
                (0..10).forEach { index ->
                    index(
                        // pass the json source (has to be on a single line)
                        source = DEFAULT_JSON.encodeToString(
                            Foo.serializer(),
                            Foo("document $index")
                        ),
                        index = "test"
                    )
                    // same as index but will fail if a document with the id already exists
                    create(
                        // you can also just pass a Serializable object directly
                        doc = Foo("another doc: $index"),
                        index = "test",
                        // specify a custom id
                        id = "doc-$index",
                    )
                    // delete a document
                    delete(id = "666", index = "test")
                }
            }
        }
        +"""
            You can of course customize the bulk session:
        """.trimIndent()
        suspendingBlock(false) {
            // bulk several parameters that you can set
            client.bulk(
                // will send a bulk request every 5 bulk operations
                // default is 100
                bulkSize = 5,
                // default index to index to
                target = "test",
                // default is wait_for
                refresh = Refresh.False
            ) {
                // these will all go into the test index
                (0..10).forEach { index ->
                    index(Foo("document $index"))
                }
            }
        }
    }

    section("Using the Repository to bulk index") {

        +"""
            Of course the `IndexRepository` supports bulk sessions as well.
        """.trimIndent()

        suspendingBlock(false) {
            val repo = client.repository("test", Foo.serializer())

            repo.bulk {
                create(Foo("will go into the test index"))
            }
        }
    }

    section("Bulk Updates") {
        suspendingBlock() {
            val repo = client.repository("test", Foo.serializer())

            repo.bulk {
                index(
                    doc = Foo("foo"),
                    id = "foo-1"
                )
                update(
                    id = "foo-1",
                    doc = """{"foo":"bar"}""",
                    docAsUpsert = true,
                )
            }
            val(doc,resp) = repo.get("foo-1")
            // will print bar
            println(doc.foo)

            repo.bulk {
                update(
                    id = "foo-1",
                    script = Script.create {
                        source="ctx._source.foo = params.p1"
                        params= mapOf(
                            "p1" to "foobar"
                        )
                    }
                )
            }
            repo.get("foo-1").let { (doc,_)->
                // prints foobar
                println(doc.foo)
            }
        }

    }

    section("Error handling with callbacks") {
        +"""
            One of the trickier things with the bulk API is error handling.
             
            To make this easy, you can use a `BulkItemCallBack` with your bulk session.
        """.trimIndent()

        suspendingBlock(false) {
            val itemCallBack = object : BulkItemCallBack {
                override fun itemFailed(
                    operationType: OperationType,
                    item: BulkResponse.ItemDetails
                ) {
                    println(
                        """
                        ${operationType.name} failed
                        ${item.id} with ${item.status}
                        """.trimMargin()
                    )
                }

                override fun itemOk(
                    operationType: OperationType,
                    item: BulkResponse.ItemDetails
                ) {
                    println(
                        """
                        operation $operationType completed! 
                        id: ${item.id}
                        sq_no: ${item.seqNo} 
                        primary_term ${item.primaryTerm}
                    """.trimIndent()
                    )
                }

                override fun bulkRequestFailed(
                    e: Exception,
                    ops: List<Pair<String, String?>>) {
                    println("""
                        Request failure ${e.message}.
                        Unless you set 
                    """.trimIndent())
                }
            }
            client.bulk(callBack = itemCallBack) {
                // invalid json would cause an error
                index("{}}")
            }
        }

        +"""
            For successful items, you might want to know what id was assigned 
            or use the seq_no and primary_term for optimistic locking.
            
            Bulk failures you might want to log or retry.
        """.trimIndent()
    }

    block {
        data class Thing(val name: String, val amount: Long = 42)
    }
}
