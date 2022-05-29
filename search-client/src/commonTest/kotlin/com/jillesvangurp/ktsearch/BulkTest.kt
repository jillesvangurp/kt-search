package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class BulkTest: SearchTestBase() {

    @Test
    fun shouldBulkIndex() = coTest {
        val index=testDocumentIndex()

        client.bulk(refresh = Refresh.WaitFor, bulkSize = 4, target = index) {
            (1..20).forEach {
                create(TestDocument(name = "doc $it").json(false))
            }
        }
        client.search(index) {

        }.total shouldBe 20
    }
}