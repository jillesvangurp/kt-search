package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test

class SniffingNodeSelectorTest : SearchTestBase() {

    @Test
    fun shouldPickSameNodeGivenSameAffinity() = coRun {

        val ids = (0..5).map {
            "thread-${Random.nextULong()}"
        }
        coroutineScope {
            val firstSelectedHosts = ids.map { id ->
                async(AffinityId(id)) {
                    id to client.restClient.nextNode().host
                }
            }.awaitAll().toMap()
            delay(1000)
            ids.map { id ->
                async(AffinityId(id)) {
                    id to client.restClient.nextNode().host
                }
            }.awaitAll().forEach { (id, host) ->
                host shouldBe firstSelectedHosts[id]
            }
        }

    }
}