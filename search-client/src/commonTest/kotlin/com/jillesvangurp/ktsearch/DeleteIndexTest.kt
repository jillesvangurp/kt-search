package com.jillesvangurp.ktsearch

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class DeleteIndexTest : SearchTestBase() {

    @Test
    fun deleteFailsWhenIndexIsNotAvailable() = coRun {
        val indexName = randomIndexName()
        val exception = shouldThrow<RestException> {
            client.deleteIndex(indexName)
        }
        exception.status shouldBe 404
        exception.message shouldContain "index_not_found_exception"
    }

    @Test
    fun deleteFailuresCanBeIgnored() = coRun {
        val indexName = randomIndexName()
        val response = client.deleteIndex(target = indexName, ignoreUnavailable = true)
        response.getValue("acknowledged").jsonPrimitive.content shouldBe "true"
    }
}