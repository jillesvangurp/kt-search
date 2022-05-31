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

    @Test
    fun shouldCallback() = coTest {
        val index = testDocumentIndex()
        var success=0
        var failed=0
        client.bulk(target = index, bulkSize = 4,callBack = object : BulkItemCallBack {
            override fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails) {
                failed++
            }

            override fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails) {
                success++
            }
        }) {
            (1..10).forEach {
                index(TestDocument(name = "doc $it").json())
            }
        }

        success shouldBe 10
        failed shouldBe 0
    }
}