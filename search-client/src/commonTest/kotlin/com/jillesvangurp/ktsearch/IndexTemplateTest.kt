package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class IndexTemplateTest : SearchTestBase() {

    @Test
    fun shouldCreateDataStream() = coRun {


        val settingsTemplateId = "test-settings"
        val mappingsTemplateId = "test-mappings"
        val templateId = "test-template"
        val dataStreamName = "test-logs"

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
            indexPatterns = listOf("test-logs*")
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