package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.Script
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.test.Test
import kotlin.test.fail

class DocumentCRUDTest: SearchTestBase() {

    @Test
    fun shouldDoDocumentCrud() = coRun {
        val index = testDocumentIndex()
        client.indexDocument(index, TestDocument("xx").json(false)).also { createResponse ->
            createResponse.index shouldBe index
            createResponse.shards.failed shouldBe 0
            client.getDocument(index, createResponse.id).also { getResponse ->
                val document = getResponse.document<TestDocument>()
                getResponse.id shouldBe createResponse.id
                document.name shouldBe "xx"
                client.indexDocument(index,TestDocument(name = "yy").json(false), id = getResponse.id).also { updateResponse ->
                    updateResponse.id shouldBe createResponse.id

                }
            }
            client.getDocument(index, createResponse.id).also { getResponse ->
                val document = getResponse.document<TestDocument>()
                document.name shouldBe "yy"
            }
            client.deleteDocument(index,createResponse.id)
            try {
                client.getDocument(index, createResponse.id)
                fail("should throw")
            } catch (e: RestException) {
                // not a problem
            }
        }
    }

    @Test
    fun shouldSupportDocumentUpdates() = coRun {
        val index = testDocumentIndex()
        client.indexDocument(index,TestDocument("foo", id=1),"1")

        client.updateDocument(index,"1", TestDocument("bar",id=1)).let { resp ->
            resp.result shouldBe "updated"
        }
        client.updateDocument(index,"1", TestDocument("bar",id=1), detectNoop = true).let { resp ->
            resp.result shouldBe "noop"
        }
    }

    @Test
    fun shouldSupportScriptUpdates() = coRun {
        val index = testDocumentIndex()
        client.updateDocument(
            target = index,
            id = "1",
            script = Script.create {
                source = "ctx._source.number += params.param1"
                params = mapOf(
                    "param1" to 1
                )
            },
            upsertJson = TestDocument("foo", number = 0),
        )
        client.updateDocument(
            target = index,
            id = "1",
            script = Script.create {
                source = "ctx._source.number += params.param1"
                params = mapOf(
                    "param1" to 1
                )
            },
            upsertJson = TestDocument("foo", number = 0),
            source = "true"
        ).let { resp ->
            resp.get?.source shouldNotBe null
            DEFAULT_JSON.decodeFromJsonElement(TestDocument.serializer(),resp.get?.source!!).let {
                it.number shouldBe 1
            }
        }
    }
}