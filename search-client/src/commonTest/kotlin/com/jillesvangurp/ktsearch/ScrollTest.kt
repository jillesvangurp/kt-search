package com.jillesvangurp.ktsearch

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test

class ScrollTest : SearchTestBase() {

    @Test
    fun deleteFailsWhenIndexIsNotAvailable() = coRun {
        val scrollId = "scrollId-${Random.nextULong()}"
        val exception = shouldThrow<RestException> {
            client.deleteScroll(scrollId)
        }
        exception.status shouldBe 400
        exception.message shouldContain "illegal_argument_exception"
    }

}