package io.inbot.eskotlinwrapper.manual

import com.fasterxml.jackson.databind.ObjectMapper
import io.inbot.eskotlinwrapper.AbstractElasticSearchTest
import io.inbot.eskotlinwrapper.IndexDAO
import io.inbot.eskotlinwrapper.JacksonModelReaderAndWriter
import io.inbot.eskotlinwrapper.ModelReaderAndWriter
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.client.crudDao
import org.elasticsearch.common.xcontent.XContentType
import org.junit.jupiter.api.Test

class CrudManualTest : AbstractElasticSearchTest(indexPrefix = "manual") {

    private data class Thing(val name: String, val amount: Long = 42)

    @Test
    fun `explain crud dao`() {
        // we have to do this twice once for printing and once for using :-)
        val modelReaderAndWriter =
            JacksonModelReaderAndWriter(Thing::class, ObjectMapper().findAndRegisterModules())
        // Create a Data Access Object

        val thingDao = esClient.crudDao("things", modelReaderAndWriter)
        // make sure we get rid of the things index before running the rest of this
        thingDao.deleteIndex()

        KotlinForExample.markdownPage(crudPage) {
            +""" 
                To do anything with Elasticsearch we have to store documents in some index. The Java client
                provides everything you need to do this but using it the right way requires quite a bit of boiler plate 
                as well as a deep understanding of what needs doing.
                
                An important part of this library is providing a user friendly abstraction for this that 
                should be familiar if you've ever written a database application using modern frameworks such
                as Spring, Ruby on Rails, etc. In such frameworks a Data Access Object or Repository object 
                provides primitives for interacting with objects in a particular database table.
                
                Wee provide a similar abstraction the ${mdLink(IndexDAO::class)}. You create one for each 
                index that you have and it allows you to do Create, Read, Update, and Delete (CRUD) operations as well as 
                a few other things.
                
                Since Elasticsearch stores Json documents, we'll want to use a data class to represent on the 
                Kotlin side and let the `IndexDAO` take care of serializing/deserializing.
                
                ## Creating an `IndexDAO`
                
                Lets use a simple data class with a few fields.
            """

            block {
                data class Thing(val name: String, val amount: Long = 42)
            }

            +"""
                Now we can create an `IndexDAO` for our `Thing` using the `crudDao` extension function:
            """

            block() {
                // we pass in the index name
                val thingDao = esClient.crudDao<Thing>("things")
            }

            +"""
                ## Creating the index
                
                Before we store any objects, we should create the index. Note this is optional but using
                Elasticsearch in schema-less mode is probably not what you want. We use a simple mapping here.
            """

            block(true) {
                thingDao.createIndex {
                    source(
                        """
                            {
                              "settings": {
                                "index": {
                                  "number_of_shards": 3,
                                  "number_of_replicas": 0,
                                  "blocks": {
                                    "read_only_allow_delete": "false"
                                  }
                                }
                              },
                              "mappings": {
                                "properties": {
                                  "name": {
                                    "type": "text"
                                  },
                                  "amount": {
                                    "type": "long"
                                  }
                                }
                              }
                            }
                        """, XContentType.JSON)
                }
            }

            +"""
                ## CRUD operations
                
                Now that we have an index, we can use the CRUD operations.
            """

            blockWithOutput {
                println("Object does not exist yet so we get back: ${thingDao.get("first")}")
                // so lets store something
                thingDao.index("first", Thing("A thing", 42))

                println("Now we get back our object: ${thingDao.get("first")}")
            }

            +"""
                You can't index an object twice unless you opt in to it being overwritten.
            """

            blockWithOutput {
                try {
                    thingDao.index("first", Thing("A thing", 42))
                } catch (e: ElasticsearchStatusException) {
                    println("we already had one of those and es returned ${e.status().status}")
                }
                // this how you do upserts
                thingDao.index("first", Thing("Another thing", 666), create = false)
                println("It was changed: ${thingDao.get("1")}")
            }

            +"""
                Of course deleting an object is also possible.
            """
            blockWithOutput {
                thingDao.delete("1")
                println(thingDao.get("1"))
            }

            +"""
                ## Updates with optimistic locking
                
                One useful feature in Elasticsearch is that it can do optimistic locking. The way this works is
                that it keeps track of a `sequenceNumber` and `primaryTerm` for each document. 
                If you provide both in index, it will check that what you provide matches what it has and only
                overwrite the document if that lines up.
                
                This works as follows.
            """
            blockWithOutput {
                thingDao.index("2", Thing("Another thing"))

                val (obj, rawGetResponse) = thingDao.getWithGetResponse("2")
                    ?: throw IllegalStateException("We just created this?!")

                println("obj with id '${obj.name}' has id: ${rawGetResponse.id}, " +
                        "primaryTerm: ${rawGetResponse.primaryTerm}, and " +
                        "seqNo: ${rawGetResponse.seqNo}")
                // This works
                thingDao.index(
                    "2",
                    Thing("Another Thing"),
                    seqNo = rawGetResponse.seqNo,
                    primaryTerm = rawGetResponse.primaryTerm,
                    create = false
                )
                try {
                    // ... but if we use these values again it fails
                    thingDao.index(
                        "2",
                        Thing("Another Thing"),
                        seqNo = rawGetResponse.seqNo,
                        primaryTerm = rawGetResponse.primaryTerm,
                        create = false
                    )
                } catch (e: ElasticsearchStatusException) {
                    println("Version conflict! Es returned ${e.status().status}")
                }
            }

            +"""     
                While you can do this manually, the Kotlin client makes optimistic locking a bit easier by 
                providing a robust update method instead.
            """
            blockWithOutput {
                thingDao.index("3", Thing("Yet another thing"))

                thingDao.update("3") { currentThing ->
                    currentThing.copy(name = "an updated thing", amount = 666)
                }

                println("It was updated: ${thingDao.get("3")}")

                thingDao.update("3") { currentThing ->
                    currentThing.copy(name = "we can do this again and again", amount = 666)
                }

                println("It was updated again ${thingDao.get("3")}")
            }

            +"""
                Update simply does the same as we did manually earlier: it gets the current version of the object
                along with the metadata. It then passes the current version to the update lambda function where
                you can do with it what you want. In this case we simply use Kotlin's copy to create a copy and 
                modify one of the fields and then we return it as the new value. 
                
                The `update` method  traps the version conflict and retries a configurable number of times. 
                Conflicts can happen if you have concurrent writes to the same object. The retry gets the latest 
                version and applies the update lambda again and then attempts to store that.
                
                To simulate what happens without retries, we can throw some threads at this and configure 0
                retries:
            """

            blockWithOutput {
                thingDao.index("4", Thing("First version of the thing", amount = 0))

                try {
                    1.rangeTo(30).toList().parallelStream().forEach { n ->
                        // the maxUpdateTries parameter is optional and has a default value of 2
                        // so setting this to 0 and doing concurrent updates is going to fail
                        thingDao.update("4", 0) { Thing("nr_$n") }
                    }
                } catch (e: Exception) {
                    println("It failed because we disabled retries and we got a conflict")
                }
            }
            +"""
                Doing the same with 10 retries, fixes the problem.
            """
            blockWithOutput {
                thingDao.index("5", Thing("First version of the thing", amount = 0))

                1.rangeTo(30).toList().parallelStream().forEach { n ->
                    // but if we let it retry a few times, it will be eventually consistent
                    thingDao.update("5", 10) { Thing("nr_$n", amount = it.amount + 1) }
                }
                println("All updates succeeded! amount = ${thingDao.get("5")?.amount}.")
            }

            +"""
               ## Custom serialization 
               
               If you want to customize how serialization and deserialization works, you can pass in a
               ${mdLink(ModelReaderAndWriter::class)} implementation.
               
               The default value of this is an instance of the included JacksonModelReaderAndWriter, which
               uses Jackson to serialize and deserialize our `Thing` objects. 
               
               If you don't want the default Jackson based serialization, or if you want to customize the jackson 
               object mapper, you simply create your own instance and pass it to the `IndexDAO`.
            """
            block() {
                // this is what is used by default but you can use your own implementation
                val modelReaderAndWriter = JacksonModelReaderAndWriter(
                    Thing::class,
                    ObjectMapper().findAndRegisterModules()
                )

                val thingDao = esClient.crudDao<Thing>(
                    index = "things", modelReaderAndWriter = modelReaderAndWriter)
            }
        }
    }
}
