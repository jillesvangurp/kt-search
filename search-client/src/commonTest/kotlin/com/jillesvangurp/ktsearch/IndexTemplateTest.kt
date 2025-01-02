package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import io.kotest.matchers.shouldBe
import kotlin.random.Random
import kotlin.random.nextULong
import kotlin.test.Test

class IndexTemplateTest : SearchTestBase() {

    @Test
    @IgnoreJs // keeps failing with some weird fail to fetch error in js/wasm, no idea why
    fun shouldCreateDataStream() = coRun {

        val suffix = Random.nextULong()
        val settingsTemplateId = "test-settings-$suffix"
        val mappingsTemplateId = "test-mappings-$suffix"
        val templateId = "test-template-$suffix"
        val dataStreamName = "test-logs-$suffix"

        runCatching { client.deleteDataStream(dataStreamName) }
        runCatching { client.deleteIndexTemplate(templateId) }
        runCatching { client.deleteComponentTemplate(mappingsTemplateId) }
        runCatching { client.deleteComponentTemplate(settingsTemplateId) }

        client.updateComponentTemplate(settingsTemplateId) {
            settings {
                replicas = 4
            }
        }
        client.updateComponentTemplate(mappingsTemplateId) {
            mappings {
                text("name")
                keyword("category")
            }
        }
        client.createIndexTemplate(templateId) {
            indexPatterns = listOf("test-logs-$suffix*")
            dataStream = JsonDsl()
            composedOf = listOf(settingsTemplateId, mappingsTemplateId)
        }

        client.exists(dataStreamName) shouldBe false
        client.createDataStream(dataStreamName)
        client.exists(dataStreamName) shouldBe true
        client.deleteDataStream(dataStreamName)

        client.deleteIndexTemplate(templateId)
        client.deleteComponentTemplate(mappingsTemplateId)
        client.deleteComponentTemplate(settingsTemplateId)
    }
}