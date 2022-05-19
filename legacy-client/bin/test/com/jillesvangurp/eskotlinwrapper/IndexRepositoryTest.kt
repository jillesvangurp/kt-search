package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import org.elasticsearch.ElasticsearchStatusException
import org.elasticsearch.action.support.WriteRequest
import org.elasticsearch.client.configure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IndexRepositoryTest : AbstractElasticSearchTest(indexPrefix = "crud") {
    @Test
    fun `index and delete a document`() {
        val id = randomId()
        repository.index(id, TestModel("hi"))
        assertThat(repository.get(id)).isEqualTo(TestModel("hi"))
        repository.delete(id)
        assertThat(repository.get(id)).isNull()
    }

    @Test
    fun `it should index fine without an id and autoassign one`() {
        val obj = TestModel("ohai!")
        val newId = repository.index(null, obj).id
        assertThat(repository.get(newId)).isEqualTo(obj)
    }

    @Test
    fun `update a document using a lambda function to transform what we have in the index`() {
        val id = randomId()
        repository.index(id, TestModel("hi"))
        // we use optimistic locking
        // this fetches the current version of the document and applies the lambda function to it
        repository.update(id) { TestModel("bye") }
        assertThat(repository.get(id)!!.message).isEqualTo("bye")
    }

    @Test
    fun `Concurrent updates succeed and resolve conflicts without interference`() {
        val id = randomId()
        repository.index(id, TestModel("hi"))

        // do some concurrent updates; without retries this will fail
        0.rangeTo(30).toList().parallelStream().forEach { n ->
            // the maxUpdateTries parameter is optional and has a default value of 2
            // you will see in the logs that it actually has to retry a few times
            repository.update(id, 10) { TestModel("nr_$n") }
        }
    }

    @Test
    fun `produce conflict if you provide the wrong seqNo or primaryTerm`() {
        val id = randomId()
        // lets index a document
        repository.index(id, TestModel("hi"))

        assertThrows<ElasticsearchStatusException> {
            // that version is wrong ...
            repository.index(id, TestModel("bar"), create = false, seqNo = 666, primaryTerm = 666)
        }
        // you can do manual optimistic locking
        repository.index(id, TestModel("bar"), create = false, seqNo = 0, primaryTerm = 1)
        assertThat(repository.get(id)!!.message).isEqualTo("bar")
    }

    @Test
    fun `it should wait until committed`() {
        repository.index("1", TestModel("one"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
        repository.index("2",TestModel("another one"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
        repository.index("3",TestModel("one more"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
        assertThat(repository.search {  }.total).isEqualTo(3)
    }

    @Test
    fun `create and update a mapping`() {
        repository.deleteIndex()
        repository.createIndex {
            configure {
                mappings {
                    text("foo")
                }
            }
        }
        repository.updateIndexMapping {
            configure {
                keyword("bar")
            }
        }
        repository.refresh()
        val mappings = repository.getMappings()
        val indexMapping = mappings.mappings()[indexName]
        val source = indexMapping?.source()
        assertThat(source).isNotNull()
        source?.let {
            // the response class API is not great, so lets just do a string contains
            assertThat(it.toString()).contains("bar")
        }
    }
}
