package com.jillesvangurp.ktsearch

import com.jillesvangurp.searchdsls.mappingdsl.IndexSettingsAndMappingsDSL
import kotlinx.serialization.Serializable

@Serializable
data class TestDocument(
    val name: String,
    val description: String?=null,
    val number: Long?=null,
    val tags: List<String>?=null
) {
    companion object {
        val mapping = IndexSettingsAndMappingsDSL().apply {
            mappings(dynamicEnabled = false) {
                text(TestDocument::name)
                text(TestDocument::description)
                number<Long>(TestDocument::number)
                keyword(TestDocument::tags)
            }
        }
    }

    fun json() = DEFAULT_PRETTY_JSON.encodeToString(serializer(), this)
}

