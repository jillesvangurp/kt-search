package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.querydsl.Script
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BulkTest : SearchTestBase() {

    @Test
    fun shouldBulkIndex() = coRun {
        testDocumentIndex { index ->


            client.bulk(refresh = Refresh.WaitFor, bulkSize = 4, target = index) {
                (1..20).forEach {
                    create(TestDocument(name = "doc $it").json(false))
                }
            }
            client.search(index) {

            }.total shouldBe 20
        }
    }

    @Test
    fun shouldCallback() = coRun {
        testDocumentIndex { index ->

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
    }

    @Test
    fun shouldHandleSourceFieldOnUpdate() = coRun {
        testDocumentIndex { index ->
            client.bulk(target = index, source = "true") {
                update(TestDocument("bar"), id = "1", docAsUpsert = true)
            }
        }
    }

    @Test
    fun shouldHandleScriptUpdate() = coRun {
        testDocumentIndex { index ->
            client.bulk(target = index, source = "true") {
                // will get initialized to 0 by the upsert
                update(script = Script.create {
                    source = "ctx._source.number += params.param1"
                    params = mapOf(
                        "param1" to 10
                    )
                }, id = "1", upsert = TestDocument("counter", number = 0))
                // now the script runs
                update(script = Script.create {
                    source = "ctx._source.number += params.param1"
                    params = mapOf(
                        "param1" to 10
                    )
                }, id = "1", upsert = TestDocument("counter", number = 0))
            }
        }
    }

    @Test
    fun shouldAcceptSerializedUpdate() = coRun {
        testDocumentIndex { index ->
            client.bulk(target = index, source = "true") {
                // will get initialized to 0 by the upsert

                create(TestDocument("original"), id = "42")
                // now the script runs
                update(TestDocument("changed"), id = "42")
            }
            val resp = client.getDocument(index, "42").source!!.parse<TestDocument>()
            resp.name shouldBe "changed"
        }
    }

    @Test
    fun shouldOverrideBulkRoutingForItemsWithRouting() = coRun {
        testDocumentIndex { index ->
            client.bulk(target = index, source = "true", routing = "1") {
                create(doc = TestDocument(name = "document with specific routing"), id = "42", routing = "2")
                create(doc = TestDocument(name = "document without specific routing"), id = "43")
                index(doc = TestDocument(name = "document to delete"), id = "44", routing = "3")

                update(
                    id = "42",
                    doc = TestDocument(name = "document with specific routing updated").json(),
                    routing = "2"
                )

                delete(id = "44", routing = "3")
            }

            val docWithSpecificRouting = client.getDocument(index, "42")
            val docWithoutSpecificRouting = client.getDocument(index, "43")

            shouldThrow<RestException> {
                client.getDocument(index, "44")
            }.message shouldContain "RequestIsWrong 404"

            docWithSpecificRouting.routing shouldBe "2"
            docWithSpecificRouting.source!!.parse<TestDocument>().name shouldBe "document with specific routing updated"
            docWithoutSpecificRouting.routing shouldBe "1"
        }
    }

    @Test
    fun shouldRestoreTemporaryBulkIndexingSettings() = coRun {
        testDocumentIndex { index ->
            val before = indexSettingPair(client.getIndexSettings(index), index)
            client.bulk(
                target = index,
                bulkSize = 2,
                disableRefreshInterval = true,
                setReplicasToZero = true,
            ) {
                index(TestDocument(name = "optimized"), id = "optimized")
            }
            val after = indexSettingPair(client.getIndexSettings(index), index)
            after shouldBe before
        }
    }
}

private fun indexSettingPair(settings: JsonObject, index: String): Pair<String?, String?> {
    val indexObj = settings[index]?.jsonObject
    val refreshInterval = indexObj
        ?.get("settings")?.jsonObject
        ?.get("index")?.jsonObject
        ?.get("refresh_interval")?.jsonPrimitive
        ?.contentOrNull
    val replicas = indexObj
        ?.get("settings")?.jsonObject
        ?.get("index")?.jsonObject
        ?.get("number_of_replicas")?.jsonPrimitive
        ?.contentOrNull
    return refreshInterval to replicas
}
