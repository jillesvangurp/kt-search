package com.jillesvangurp.ktsearch

import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class SearchResponseDefaultsTest {

    @Test
    fun shouldParseDefaults() {
        val parsed = DEFAULT_JSON.decodeFromString(ExtendedStatsBucketResult.serializer(),"{}")
        parsed.min shouldBe 0.0
        parsed.stdDeviationBounds.lower shouldBe 0.0
    }
}