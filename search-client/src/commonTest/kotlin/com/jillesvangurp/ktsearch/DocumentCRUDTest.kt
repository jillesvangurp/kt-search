package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class DocumentCRUDTest: SearchTest() {

    @Test
    fun shouldCreateDocument() = coTest {
        val index = testDocumentIndex()
        val response = client.createDocument(index, TestDocument("xx").json())
        response.index shouldBe index
        response.shards.failed shouldBe 0
    }
}