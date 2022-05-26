package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SimpleApiTest : SearchTest()  {

    @Test
    fun clusterShouldBeHealthy() = coTest {
        client.clusterHealth().status shouldBe ClusterStatus.Yellow
    }
}