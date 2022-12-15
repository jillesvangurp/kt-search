package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import kotlin.test.Test

class IndexTemplateTest: SearchTestBase() {

    @Test
    fun shouldCreateDataStream() = coTest {
        val settingsTemplateId = "my-settings"
        val mappingsTemplateId = "my-mappings"
        val templateId = "my-template"
        val dataStreamName = "logs"
        client.updateComponentTemplate(settingsTemplateId) {
            settings {
                replicas=4
            }
        }
        client.updateComponentTemplate(mappingsTemplateId) {
            mappings {
                text("name")
                keyword("category")
            }
        }
        client.createIndexTemplate(templateId) {
            indexPatterns = listOf("logs*")
            dataStream = JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase)
            composedOf = listOf(settingsTemplateId, mappingsTemplateId)
        }

        client.createDataStream(dataStreamName)
        client.deleteDataStream(dataStreamName)

        client.deleteIndexTemplate(templateId)
        client.deleteComponentTemplate(mappingsTemplateId)
        client.deleteComponentTemplate(settingsTemplateId)
    }
}