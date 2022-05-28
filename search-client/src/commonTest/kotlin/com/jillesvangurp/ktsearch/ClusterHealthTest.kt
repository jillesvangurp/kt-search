package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ClusterHealthTest : SearchTestBase()  {

    @Test
    fun clusterShouldBeHealthy() = runTest {
        client.clusterHealth().status shouldNotBe ClusterStatus.Red
    }


}