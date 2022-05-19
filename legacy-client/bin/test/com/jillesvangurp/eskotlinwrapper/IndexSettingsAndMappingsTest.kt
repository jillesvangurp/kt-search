package com.jillesvangurp.eskotlinwrapper

import assertk.assertThat
import assertk.assertions.contains
import com.jillesvangurp.eskotlinwrapper.IndexSettingsAndMappingsDSL.Companion.indexSettingsAndMappings
import org.elasticsearch.xcontent.stringify
import org.junit.jupiter.api.Test

class IndexSettingsAndMappingsTest {

    @Test
    fun `should create simple mapping`() {
        val json = indexSettingsAndMappings(generateMetaFields = true, pretty = true) {
            settings {
                replicas = 2
                shards = 3
            }
            mappings {
                text("title") {
                    fields {
                        keyword("keyword")
                    }
                }
                text("description")
                keyword("tag")

                field("geo", "geo_point")
                // add whatever object as a mapping for type foo
                // whatever the dsl does not support you can do like this.
                this["foo"] = mapOf("type" to "text")
            }
        }.stringify()

        println(json)
        assertThat(json).contains("index.number_of_replicas")
        assertThat(json).contains("index.number_of_shards")
    }
}
