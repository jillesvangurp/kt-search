package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldNotBe
import kotlin.test.Test

class ClusterHealthTest : SearchTestBase()  {

    @Test
    fun clusterShouldBeHealthy() = coRun {
        client.clusterHealth().status shouldNotBe ClusterStatus.Red
    }


}