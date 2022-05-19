package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.querydsl.SearchDSL
import com.jillesvangurp.searchdsls.querydsl.matchAll
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.runBlocking
import org.elasticsearch.action.search.configure
import org.elasticsearch.action.search.dsl
import org.elasticsearch.action.support.WriteRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

class AsyncIndexRepositoryTest : AbstractAsyncElasticSearchTest(indexPrefix = "crud") {
    @Test
    fun `index and delete a document`() {
        runBlocking {
            val id = randomId()
            repository.index(id, TestModel("hi"))
            assertThat(repository.get(id)).isEqualTo(TestModel("hi"))
            repository.delete(id)
            assertThat(repository.get(id)).isNull()
        }
    }

    @Test
    fun `it should index fine without an id and autoassign one`() {
        runBlocking {
            val obj = TestModel("ohai!")
            val newId = repository.index(null, obj).id
            assertThat(repository.get(newId)).isEqualTo(obj)
        }
    }

    @Test
    fun `update a document using a lambda function to transform what we have in the index`() {
        runBlocking {
            val id = randomId()
            repository.index(id, TestModel("hi"))
            // we use optimistic locking
            // this fetches the current version of the document and applies the lambda function to it
            repository.update(id) { TestModel("bye") }
            assertThat(repository.get(id)!!.message).isEqualTo("bye")
        }
    }

    @Test
    fun `Concurrent updates succeed and resolve conflicts without interference`() {
        runBlocking {

            val id = randomId()
            repository.index(id, TestModel("hi"))

            val jobs = mutableListOf<Deferred<Any>>()
            // do some concurrent updates; without retries this will fail
            for (n in 0.rangeTo(10)) {
                // this requires using multiple threads otherwise the failures will pile up
                jobs.add(
                    async(Dispatchers.Unconfined) {
                        repository.update(id, 10) { TestModel("nr_$n") }
                    }
                )
            }
            awaitAll(*jobs.toTypedArray())
        }
    }

    @Test
    fun `produce conflict if you provide the wrong seqNo or primaryTerm`() {
        runBlocking {

            val id = randomId()
            // lets index a document
            repository.index(id, TestModel("hi"))

            try {
                // that version is wrong ...
                repository.index(id, TestModel("bar"), create = false, seqNo = 666, primaryTerm = 666)
                fail { "expected a version conflict" }
            } catch (e: Exception) {
            }
            // you can do manual optimistic locking
            repository.index(id, TestModel("bar"), create = false, seqNo = 0, primaryTerm = 1)
            assertThat(repository.get(id)!!.message).isEqualTo("bar")
        }
    }

    @Test
    fun `async scrolling search`() {
        runBlocking {
            repository.bulk {
                (1..100).forEach {
                    index("$it", TestModel("m-$it"))
                }
            }
            repository.refresh()
            val count = repository.search(scrolling = true) {
                configure {
                    resultSize = 5
                }
            }.mappedHits.count()
            assertThat(count).isEqualTo(100)
        }
    }

    @Test
    fun `async raw json test`() {
        runBlocking {
            repository.bulk {
                (1..10).forEach {
                    index("$it", TestModel("m-$it"))
                }
            }
            repository.refresh()
            val results = repository.jsonSearch(SearchDSL().apply {
                query = matchAll()
            }.json())
            assertThat(results.total).isEqualTo(10)
        }
    }

    @Test
    fun `it should wait until committed`() {
        runBlocking {
            repository.index("1", TestModel("one"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
            repository.index("2",TestModel("another one"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
            repository.index("3",TestModel("one more"), refreshPolicy = WriteRequest.RefreshPolicy.WAIT_UNTIL)
            assertThat(repository.search {  }.total).isEqualTo(3)
        }
    }
}
