package com.jillesvangurp.ktsearch

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test

class SimpleApiTest : SearchTest()  {

    @Test
    fun clusterShouldBeHealthy() = coTest {
        val response = client.get {
            it.path("_cluster","health")
        }.getOrThrow()
        response.text shouldContain "yellow"
    }
}