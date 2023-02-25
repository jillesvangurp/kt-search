@file:Suppress("unused")

package com.jillesvangurp.ktsearch

import com.jillesvangurp.jsondsl.JsonDsl
import com.jillesvangurp.jsondsl.json
import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.serialization.json.JsonObject

class ComponentTemplate: JsonDsl() {
    var template by property(defaultValue = IndexSettingsAndMappingsDSL())
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

class IndexTemplate: JsonDsl() {
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

suspend fun SearchClient.exists(name: String): Boolean {
    restClient.head {
        path(name)
    }.let {
        if(it.status<300) {
            return true
        } else if(it.status==404) {
            return false
        } else {
            throw it.asResult().exceptionOrNull()?: error("should have an exception")
        }
    }
}
suspend fun SearchClient.deleteDataStream(name: String): JsonObject {
    return restClient.delete {
        path("_data_stream", name)
    }.parseJsonObject()
}
