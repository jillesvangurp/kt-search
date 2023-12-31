package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.Script
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.nondeterministic.eventuallyConfig
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class BulkTest : SearchTestBase() {

    @Test
    fun shouldBulkIndex() = coRun {
        val index = testDocumentIndex()

        client.bulk(refresh = Refresh.WaitFor, bulkSize = 4, target = index) {
            (1..20).forEach {
                create(TestDocument(name = "doc $it").json(false))
            }
        }
        client.search(index) {

        }.total shouldBe 20
    }

    @Test
    fun shouldCallback() = coRun {
        val index = testDocumentIndex()
        var success = 0
        var failed = 0
        client.bulk(target = index, bulkSize = 4, callBack = object : BulkItemCallBack {
            override fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails) {
                failed++
            }

            override fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails) {
                success++
            }

            override fun bulkRequestFailed(e: Exception, ops: List<Pair<String, String?>>) {
                error("${e.message} on")
            }
        }) {
            (1..10).forEach {
                index(TestDocument(name = "doc $it").json())
            }
        }

        success shouldBe 10
        failed shouldBe 0
    }

    @Test
    fun shouldHandleSourceFieldOnUpdate() = coRun {
        val index = testDocumentIndex()
        client.bulk(target = index, source="true") {
            update(TestDocument("bar"), id="1", docAsUpsert = true)
        }
    }

    @Test
    fun shouldHandleScriptUpdate() = coRun {
        val index = testDocumentIndex()
        client.bulk(target = index, source="true") {
            // will get initialized to 0 by the upsert
            update(script = Script.create {
                source="ctx._source.number += params.param1"
                params= mapOf(
                    "param1" to 10
                )
            }, id = "1", upsert = TestDocument("counter", number = 0))
            // now the script runs
            update(script = Script.create {
                source="ctx._source.number += params.param1"
                params= mapOf(
                    "param1" to 10
                )
            }, id = "1", upsert = TestDocument("counter", number = 0))
        }
    }

    @Test
    fun shouldAcceptSerializedUpdate() = coRun {
        val index = testDocumentIndex()
        client.bulk(target = index, source="true") {
            // will get initialized to 0 by the upsert

            create(TestDocument("original"), id = "42")
            // now the script runs
            update(TestDocument("changed"), id = "42")
        }
        val resp = client.getDocument(index,"42").source!!.parse<TestDocument>()
        resp.name shouldBe "changed"
    }
}