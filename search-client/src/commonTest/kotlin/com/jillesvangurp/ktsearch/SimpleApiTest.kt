package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SimpleApiTest : SearchTest()  {

    @Test
    fun clusterShouldBeHealthy() = coTest {
        client.clusterHealth().status shouldBe ClusterStatus.Yellow
    }

    @Test
    fun createIndex() = coTest {
        val response = client.createIndex("foo") {
            mappings(false) {
                keyword("foo")
                number<Long>("bar")
            }
        }
        println(response.getOrThrow().text)
    }
}