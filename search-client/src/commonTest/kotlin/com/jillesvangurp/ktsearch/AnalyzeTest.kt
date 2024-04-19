package com.jillesvangurp.ktsearch

import io.kotest.matchers.shouldBe
import kotlin.test.Test

class AnalyzeTest : SearchTestBase() {

    @Test
    fun shouldAnalyzeQuery() = coRun {
        val resp = client.analyze {
            text = listOf("foo bar")
            analyzer = "standard"
        }

        resp.tokens.size shouldBe 2
    }
}

