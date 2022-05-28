package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DocumentCRUDTest: SearchTest() {

    @Test
    fun shouldDoDocumentCrud() = coTest {
        val index = testDocumentIndex()
        client.indexDocument(index, TestDocument("xx").json()).also {createResponse ->
            createResponse.index shouldBe index
            createResponse.shards.failed shouldBe 0
            client.getDocument(index, createResponse.id).also { getResponse ->
                val document = getResponse.document<TestDocument>()
                getResponse.id shouldBe createResponse.id
                document.name shouldBe "xx"
                client.indexDocument(index,TestDocument(name = "yy").json(), id = getResponse.id).also { updateResponse ->
                    updateResponse.id shouldBe createResponse.id

                }
            }
            client.getDocument(index, createResponse.id).also { getResponse ->
                val document = getResponse.document<TestDocument>()
                document.name shouldBe "yy"
            }
            client.deleteDocument(index,createResponse.id)
        }


    }
}