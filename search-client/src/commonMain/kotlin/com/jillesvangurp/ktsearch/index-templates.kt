package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.PropertyNamingConvention
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.serialization.json.JsonObject

class ComponentTemplate: JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var template by property<IndexSettingsAndMappingsDSL>(defaultValue = IndexSettingsAndMappingsDSL())
}

suspend fun SearchClient.updateComponentTemplate(templateId: String, block: IndexSettingsAndMappingsDSL.()->Unit): JsonObject {
    return restClient.put {
        path("_component_template",templateId)
        body = ComponentTemplate().also {
            it.template.apply(block)
        }.json(true)
    }.parseJsonObject()
}

suspend fun SearchClient.deleteComponentTemplate(templateId: String): JsonObject {
    return restClient.delete {
        path("_component_template",templateId)
    }.parseJsonObject()
}

class IndexTemplate: JsonDsl(namingConvention = PropertyNamingConvention.ConvertToSnakeCase) {
    var indexPatterns by property(defaultValue = listOf<String>())
    var dataStream by property<JsonDsl>()
    var composedOf by property(defaultValue = listOf<String>())
    var priority by property(defaultValue = 300)
    var meta by property<Map<String,String>>(customPropertyName = "_meta")

}

suspend fun SearchClient.createIndexTemplate(templateId: String, block: IndexTemplate.() -> Unit): JsonObject {
    return restClient.put {
        path("_index_template", templateId)
        body = IndexTemplate().apply(block).json(true)
    }.parseJsonObject()
}

suspend fun SearchClient.deleteIndexTemplate(templateId: String): JsonObject {
    return restClient.delete {
        path("_index_template", templateId)
    }.parseJsonObject()
}

suspend fun SearchClient.createDataStream(name: String): JsonObject {
    return restClient.put {
        path("_data_stream", name)
    }.parseJsonObject()
}
suspend fun SearchClient.deleteDataStream(name: String): JsonObject {
    return restClient.delete {
        path("_data_stream", name)
    }.parseJsonObject()
}
