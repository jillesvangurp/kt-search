package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
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
}