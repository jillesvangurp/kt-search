package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.deleteByQuery
import com.jillesvangurp.ktsearch.getIndexMappings
import com.jillesvangurp.ktsearch.parseHits
import com.jillesvangurp.ktsearch.search
import com.jillesvangurp.ktsearch.total
import com.jillesvangurp.searchdsls.querydsl.matchAll
import com.jillesvangurp.searchdsls.querydsl.range
import com.jillesvangurp.searchdsls.querydsl.term
import com.jillesvangurp.serializationext.DEFAULT_JSON
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import org.slf4j.MDC

val logger = KotlinLogging.logger {}

class AppenderTest {
    @Test
    fun shouldLogSomeStuff() = runBlocking {
        repeat(2) {
            logSomeStuff()
        }
    }

    private suspend fun logSomeStuff() {
        val client = SearchClient(
            KtorRestClient(
                host = "localhost",
                port = 9990,
            )
        )
        client.manageDataStream(
            prefix = "applogs",
            hotRollOverGb = 1,
            hotMaxAge = "1d",
            numberOfReplicas = 0,
            numberOfShards = 1,
            warmMinAge = 1.days,
            deleteMinAge = 7.days,
            warmShrinkShards = 1,
            warmSegments = 1,
            configureIlm = true,
        )

        // delete messages from previous runs
        client.deleteByQuery("applogs") {
            query = matchAll()
        }

        logger.info { "hello world" }
        MDC.put("test", "value")
        MDC.put("duration_ms", "42")
        MDC.put("amount_processed", "2")
        MDC.put("improbability_level", "0.01")

        logger.error { "another one" }
        try {
            error("oopsie. This is not an error - don't worry, just  testing stack trace logging here")
        } catch (e: Exception) {
            logger.error(e) { "stacktrace" }
        }
        MDC.put("duration_ms", "420")
        MDC.put("amount_processed", "5")
        MDC.put("improbability_level", "0.9")
        logger.warn { "last one" }
        MDC.clear()
        delay(1.seconds)

        eventually(20.seconds) {
            // should not throw because templates and the data stream were pre-created
            client.getIndexMappings("applogs")
            // if our mapping is applied, we should be able to query on context.environment
            val resp = client.search("applogs") {
                resultSize=100
                query = term("context.environment", "tests")
            }
            resp.total shouldBeGreaterThan 2
            val hits = resp.parseHits<LogMessage>(DEFAULT_JSON)
            hits.first().let { m ->
                (m.context?.keys ?: setOf()) shouldContain "host"
                (m.context?.keys ?: setOf()) shouldNot contain("exclude")
            }
            hits.first { it.message == "stacktrace"}.let {
                (it.exceptionList?.first()?.stackTrace?.size?:-1) shouldBeGreaterThan 1
            }
        }
        // make sure specified mdc fields are correctly coerced
        // i.e. range queries work as expected
        client.search("applogs") {
            query = range("mdc.duration_ms") {
                gt = 100
            }
        }.also {
            println(it.hits)
        }.total shouldBe 1
        client.search("applogs") {
            query = range("mdc.amount_processed") {
                gt = 3
                lt = 6
            }
        }.total shouldBe 1
        client.search("applogs") {
            query = range("mdc.improbability_level") {
                gt = 0.3
            }
        }.total shouldBe 1

    }
}
