package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class ClusterHealthTest : SearchTest()  {

    @Test
    fun clusterShouldBeHealthy() = coTest {
        client.clusterHealth().status shouldNotBe ClusterStatus.Red
    }


}